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
package org.sonar.plugins.javascript.bridge;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.javascript.JavaScriptFilePredicate;
import org.sonar.plugins.javascript.JavaScriptLanguage;
import org.sonar.plugins.javascript.TypeScriptLanguage;
import org.sonar.plugins.javascript.utils.ProgressReport;

abstract class AbstractAnalysis {

  private static final Logger LOG = Loggers.get(AbstractAnalysis.class);
  static final String PROGRESS_REPORT_TITLE = "Progress of JavaScript/TypeScript analysis";
  static final long PROGRESS_REPORT_PERIOD = TimeUnit.SECONDS.toMillis(10);

  final BridgeServer bridgeServer;
  final Monitoring monitoring;
  final AnalysisProcessor analysisProcessor;
  SensorContext context;
  ContextUtils contextUtils;
  JsTsChecks checks;
  ProgressReport progressReport;
  AnalysisMode analysisMode;
  protected final AnalysisWarningsWrapper analysisWarnings;

  AbstractAnalysis(
    BridgeServer bridgeServer,
    Monitoring monitoring,
    AnalysisProcessor analysisProcessor,
    AnalysisWarningsWrapper analysisWarnings
  ) {
    this.bridgeServer = bridgeServer;
    this.monitoring = monitoring;
    this.analysisProcessor = analysisProcessor;
    this.analysisWarnings = analysisWarnings;
  }

  protected static String inputFileLanguage(InputFile file) {
    return JavaScriptFilePredicate.isTypeScriptFile(file)
      ? TypeScriptLanguage.KEY
      : JavaScriptLanguage.KEY;
  }

  void initialize(SensorContext context, JsTsChecks checks, AnalysisMode analysisMode) {
    LOG.debug("Initializing " + getClass().getName());
    this.context = context;
    contextUtils = new ContextUtils(context);
    this.checks = checks;
    this.analysisMode = analysisMode;
  }

  protected boolean isJavaScript(InputFile file) {
    return inputFileLanguage(file).equals(JavaScriptLanguage.KEY);
  }

  abstract void analyzeFiles(List<InputFile> inputFiles, List<String> tsConfigs) throws IOException;
}
