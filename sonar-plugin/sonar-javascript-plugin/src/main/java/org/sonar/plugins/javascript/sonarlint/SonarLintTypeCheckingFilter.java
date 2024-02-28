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
package org.sonar.plugins.javascript.sonarlint;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.WildcardPattern;
import org.sonar.plugins.javascript.JavaScriptLanguage;
import org.sonar.plugins.javascript.JavaScriptPlugin;
import org.sonar.plugins.javascript.TypeScriptLanguage;
import org.sonar.plugins.javascript.filter.JavaScriptExclusionsFileFilter;

/**
 * This class partially reproduces the behavior of JavaScriptExclusionsFileFilter's implementation.
 *
 * The support of JavaScript type-checking in SonarLint context depends on the project size, which
 * is manually computed by walking the file system. During the traversal, we need to replicate the
 * plugin's file filtering logic to consider the same set of files during counting.
 *
 * The replication is partial; it limits itself to consider files with the expected extensions and
 * located in directories that should not be excluded. However, minified files and files that are
 * too big are not excluded, as they would require reading their content. Furthermore, TypeScript
 * compiler would consider them regardless on program creation.
 *
 * @see JavaScriptExclusionsFileFilter
 */
public class SonarLintTypeCheckingFilter {

  private SonarLintTypeCheckingFilter() {}

  static class FileFilter implements Predicate<Path> {

    private final Set<String> extensions = new HashSet<>();

    public FileFilter(Configuration config) {
      extensions.addAll(
        Arrays.asList(
          config
            .get(JavaScriptLanguage.FILE_SUFFIXES_KEY)
            .orElse(JavaScriptLanguage.FILE_SUFFIXES_DEFVALUE)
            .split(",")
        )
      );
      extensions.addAll(
        Arrays.asList(
          config
            .get(TypeScriptLanguage.FILE_SUFFIXES_KEY)
            .orElse(TypeScriptLanguage.FILE_SUFFIXES_DEFVALUE)
            .split(",")
        )
      );
    }

    @Override
    public boolean test(Path path) {
      return extensions.stream().anyMatch(ext -> path.toString().endsWith(ext));
    }
  }

  static class PathFilter implements Predicate<Path> {

    private final WildcardPattern[] exclusions;

    public PathFilter(Configuration config) {
      if (!isExclusionOverridden(config)) {
        exclusions = WildcardPattern.create(JavaScriptPlugin.EXCLUSIONS_DEFAULT_VALUE);
      } else {
        WildcardPattern[] jsExcludedPatterns = WildcardPattern.create(
          config.getStringArray(JavaScriptPlugin.JS_EXCLUSIONS_KEY)
        );
        WildcardPattern[] tsExcludedPatterns = WildcardPattern.create(
          config.getStringArray(JavaScriptPlugin.TS_EXCLUSIONS_KEY)
        );
        exclusions =
          concat(stream(jsExcludedPatterns), stream(tsExcludedPatterns))
            .toArray(WildcardPattern[]::new);
      }
    }

    private static boolean isExclusionOverridden(Configuration config) {
      return (
        config.get(JavaScriptPlugin.JS_EXCLUSIONS_KEY).isPresent() ||
        config.get(JavaScriptPlugin.TS_EXCLUSIONS_KEY).isPresent()
      );
    }

    @Override
    public boolean test(Path path) {
      return WildcardPattern.match(exclusions, path.toString().replaceAll("[\\\\/]", "/"));
    }
  }
}
