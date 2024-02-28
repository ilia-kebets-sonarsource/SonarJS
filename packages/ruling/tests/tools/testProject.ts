/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { Minimatch } from 'minimatch';
import {
  AnalysisInput,
  AnalysisOutput,
  FileType,
  setContext,
  toUnixPath,
} from '../../../shared/src';
import {
  DEFAULT_ENVIRONMENTS,
  DEFAULT_GLOBALS,
  JsTsFiles,
  ProjectAnalysisInput,
  ProjectAnalysisOutput,
  RuleConfig,
  analyzeProject,
  initializeLinter,
} from '../../../jsts/src';
import { accept } from '../filter/JavaScriptExclusionsFilter';
import { writeResults } from './lits';
import { analyzeHTML } from '../../../html/src';
import { isHtmlFile, isJsFile, isTsFile, isYamlFile } from './languages';
import { analyzeYAML } from '../../../yaml/src';
import projects from '../data/projects.json';

let rules: RuleConfig[] = [];

const sourcesPath = path.join(__dirname, '..', '..', '..', '..', '..', 'sonarjs-ruling-sources');
const jsTsProjectsPath = path.join(sourcesPath, 'jsts', 'projects');

const expectedPath = path.join(
  __dirname,
  '..',
  '..',
  '..',
  '..',
  'its',
  'ruling',
  'src',
  'test',
  'expected',
  'jsts',
);
const actualPath = path.join(__dirname, '..', 'actual', 'jsts');

const HTML_LINTER_ID = 'html';

type RulingInput = {
  name: string;
  testDir?: string;
  exclusions?: string;
  folder?: string;
};

const DEFAULT_EXCLUSIONS = [
  '**/.*',
  '**/.*/**',
  '**/*.d.ts',
  '**/node_modules/**',
  '**/bower_components/**',
  '**/dist/**',
  '**/vendor/**',
  '**/external/**',
].map(pattern => new Minimatch(pattern, { nocase: true }));

export function setupBeforeAll(projectFile: string) {
  rules = loadRules();
  const projectName = getProjectName(toUnixPath(projectFile));
  const project = projects.find(p => p.name === projectName);
  beforeAll(() => {
    setContext({
      workDir: path.join(os.tmpdir(), 'sonarjs'),
      shouldUseTypeScriptParserForJS: true,
      sonarlint: false,
      bundles: [],
    });
    initializeLinter(rules as RuleConfig[], DEFAULT_ENVIRONMENTS, DEFAULT_GLOBALS);
    const htmlRules = (rules as RuleConfig[]).filter(rule => rule.key !== 'no-var');
    initializeLinter(htmlRules, DEFAULT_ENVIRONMENTS, DEFAULT_GLOBALS, HTML_LINTER_ID);
  });
  return {
    project,
    expectedPath: path.join(expectedPath, project.name),
    actualPath: path.join(actualPath, project.name),
  };
}
function getProjectName(testFilePath: string) {
  const SUFFIX = '.ruling.test.ts';
  const filename = path.basename(testFilePath);
  return filename.substring(0, filename.length - SUFFIX.length);
}

/**
 * Load files and analyze project
 */
export async function testProject(rulingInput: RulingInput) {
  const projectPath = path.join(jsTsProjectsPath, rulingInput.folder ?? rulingInput.name);
  const exclusions = setExclusions(rulingInput.exclusions, rulingInput.testDir);

  const { jsTsFiles, htmlFiles, yamlFiles } = getProjectFiles(rulingInput, projectPath, exclusions);

  const payload: ProjectAnalysisInput = {
    rules: rules as RuleConfig[],
    baseDir: projectPath,
    files: jsTsFiles,
  };

  const jsTsResults = await analyzeProject(payload);
  const htmlResults = await analyzeFiles(htmlFiles, analyzeHTML, HTML_LINTER_ID);
  const yamlResults = await analyzeFiles(yamlFiles, analyzeYAML);
  const results = mergeResults(jsTsResults, htmlResults, yamlResults);

  writeResults(
    projectPath,
    rulingInput.name,
    results,
    [jsTsFiles, htmlFiles, yamlFiles],
    actualPath,
  );
}

/**
 * Creates the exclusions object
 */
function setExclusions(exclusions: string, testDir?: string) {
  const exclusionsArray = exclusions ? exclusions.split(',') : [];
  if (testDir && testDir !== '') {
    exclusionsArray.push(...testDir.split(',').map(dir => `${dir}/**/*`));
  }
  const exclusionsGlob = stringToGlob(exclusionsArray.map(pattern => pattern.trim()));
  return exclusionsGlob;

  function stringToGlob(patterns: string[]): Minimatch[] {
    return patterns.map(pattern => new Minimatch(pattern, { nocase: true, matchBase: true }));
  }
}

/**
 * Gathers all the files that should be analyzed for the given project
 */
function getProjectFiles(rulingInput: RulingInput, projectPath: string, exclusions: Minimatch[]) {
  const { jsTsFiles, htmlFiles, yamlFiles } = getFiles(projectPath, exclusions);

  if (rulingInput.testDir != null) {
    const testFolder = path.join(projectPath, rulingInput.testDir);
    getFiles(testFolder, exclusions, jsTsFiles, htmlFiles, yamlFiles, 'TEST');
  }
  return { jsTsFiles, htmlFiles, yamlFiles };
}

/**
 * Stores in `jsTsFiles`, `htmlFiles` and `yamlFiles` the files
 * found in the given `dir`, ignoring the given `exclusions` and
 * assigning the given `type`
 */
function getFiles(
  dir: string,
  exclusions: Minimatch[],
  jsTsFiles: JsTsFiles = {},
  htmlFiles: JsTsFiles = {},
  yamlFiles: JsTsFiles = {},
  type: FileType = 'MAIN',
) {
  const prefixLength = toUnixPath(dir).length + 1;
  const files = fs.readdirSync(dir, { recursive: true, withFileTypes: true });
  for (const file of files) {
    const absolutePath = toUnixPath(path.join(file.path, file.name));
    const relativePath = absolutePath.substring(prefixLength);
    if (!file.isFile()) continue;
    if (isExcluded(relativePath, exclusions)) continue;
    if (isExcluded(absolutePath, DEFAULT_EXCLUSIONS)) continue;
    const fileContent = fs.readFileSync(absolutePath, 'utf8');
    const language = findLanguage(absolutePath, fileContent);
    if (!language) continue;

    if (isHtmlFile(absolutePath)) {
      htmlFiles[absolutePath] = { fileType: type, fileContent, language };
    } else if (isYamlFile(absolutePath)) {
      yamlFiles[absolutePath] = { fileType: type, fileContent, language };
    } else {
      if (!accept(absolutePath, fileContent)) continue;
      jsTsFiles[absolutePath] = { fileType: type, fileContent, language };
    }
  }
  return { jsTsFiles, htmlFiles, yamlFiles };
}

function findLanguage(filePath: string, contents: string) {
  if (isTsFile(filePath, contents)) {
    return 'ts';
  }
  if (isJsFile(filePath)) {
    return 'js';
  }
}

function isExcluded(filePath: string, exclusions: Minimatch[]) {
  return exclusions.some(exclusion => exclusion.match(filePath));
}

/**
 * Analyze files the old school way.
 * Used for HTML and YAML
 */
async function analyzeFiles(
  files: JsTsFiles,
  analyzer: (payload: AnalysisInput) => Promise<AnalysisOutput>,
  linterId?: string,
) {
  const results = { files: {} };
  for (const [filePath, fileData] of Object.entries(files)) {
    const payload: AnalysisInput = {
      filePath,
      fileContent: fileData.fileContent,
      linterId,
    };
    try {
      const result = await analyzer(payload);
      results.files[filePath] = result;
    } catch (err) {
      results.files[filePath] = createParsingIssue(err);
    }
    results.files[filePath].language = fileData.language;
  }
  return results;
}

/**
 * Merge results from multiple analyses into a single object
 * Creates parsing issues from parsingError when needed
 */
function mergeResults(...resultsSet: ProjectAnalysisOutput[]) {
  const allResults = { files: {} };
  for (const results of resultsSet) {
    for (const [filePath, fileData] of Object.entries(results.files)) {
      if (allResults.files[filePath]) {
        throw Error(`File ${filePath} has been analyzed in multiple paths`);
      }
      if (fileData.parsingError) {
        allResults.files[filePath] = createParsingIssue({ data: fileData.parsingError });
      } else {
        allResults.files[filePath] = fileData;
      }
    }
  }
  return allResults;
}

/**
 * Creates a S2260 issue for the parsing error
 */
function createParsingIssue({
  data: { line, message },
}: {
  data: { line?: number; message: string };
}) {
  return {
    issues: [
      {
        ruleId: 'S2260',
        line,
        // stub values so we don't have to modify the type
        message,
        column: 0,
        secondaryLocations: [],
      },
    ],
  };
}

/**
 * Loading this through `fs` and not import because the file is absent at compile time
 */
function loadRules() {
  const rulesPath = path.join(__dirname, '..', 'data', 'rules.json');
  const rulesContent = fs.readFileSync(rulesPath, 'utf8');
  return JSON.parse(rulesContent);
}
