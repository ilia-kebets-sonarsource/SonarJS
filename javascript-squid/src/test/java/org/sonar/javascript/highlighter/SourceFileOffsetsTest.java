/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011 SonarSource and Eriks Nukis
 * sonarqube@googlegroups.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.javascript.highlighter;

import com.google.common.base.Charsets;
import com.sonar.sslr.api.Token;
import com.sonar.sslr.impl.Lexer;
import org.junit.Test;
import org.sonar.javascript.lexer.JavaScriptLexer;


import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class SourceFileOffsetsTest {
  private Lexer lexer = JavaScriptLexer.create(Charsets.UTF_8);

  @Test
  public void first_line(){
    String string = "hello(world)";
    SourceFileOffsets offsets = new SourceFileOffsets(string);
    List<Token> tokens = lexer.lex(string);
    assertThat(offsets.startOffset(tokens.get(0))).isEqualTo(0);
    assertThat(offsets.endOffset(tokens.get(0))).isEqualTo(5);
  }

  @Test
  public void second_line(){
    String string = "hello\nworld = 1\r\n";
    SourceFileOffsets offsets = new SourceFileOffsets(string);
    List<Token> tokens = lexer.lex(string);
    // token "="
    assertThat(offsets.startOffset(tokens.get(2))).isEqualTo(12);
    assertThat(offsets.endOffset(tokens.get(2))).isEqualTo(13);
  }
}
