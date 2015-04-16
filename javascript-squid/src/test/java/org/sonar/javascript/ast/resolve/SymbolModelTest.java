/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011 SonarSource and Eriks Nukis
 * dev@sonar.codehaus.org
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
package org.sonar.javascript.ast.resolve;

import com.google.common.collect.ImmutableList;
import com.sonar.sslr.api.AstNode;
import org.junit.Before;
import org.junit.Test;
import org.sonar.javascript.model.JavaScriptTreeModelTest;
import org.sonar.javascript.model.interfaces.Tree;
import org.sonar.javascript.model.interfaces.declaration.ScriptTree;

import java.io.File;
import java.util.LinkedList;

import static org.fest.assertions.Assertions.assertThat;

public class SymbolModelTest extends JavaScriptTreeModelTest {

  private SymbolModel SYMBOL_MODEL;

  @Before
  public void setUp() throws Exception {
    AstNode root = p.parse(new File("src/test/resources/ast/resolve/symbolModel.js"));
    SYMBOL_MODEL = SymbolModel.createFor((ScriptTree) root, null, null);
  }

  @Test
  public void symbols_filtering(){
    assertThat(SYMBOL_MODEL.getSymbols()).hasSize(10);

    assertThat(SYMBOL_MODEL.getSymbols(Symbol.Kind.FUNCTION)).hasSize(2); // eval, f
    assertThat(SYMBOL_MODEL.getSymbols(Symbol.Kind.PARAMETER)).hasSize(1); // p
    assertThat(SYMBOL_MODEL.getSymbols(Symbol.Kind.PARAMETER, Symbol.Kind.FUNCTION)).hasSize(3);

    assertThat(SYMBOL_MODEL.getSymbols("a")).hasSize(3);
    assertThat(SYMBOL_MODEL.getSymbols(ImmutableList.of("a", "arguments"))).hasSize(4);
    assertThat(SYMBOL_MODEL.getSymbols(new LinkedList<String>())).hasSize(10);
  }

  @Test
  public void symbols_scope(){
    assertThat(SYMBOL_MODEL.getScopes()).hasSize(3); // script/global, f, catch

    Symbol f = SYMBOL_MODEL.getSymbols("f").get(0);
    Symbol e = SYMBOL_MODEL.getSymbols("e").get(0);
    assertThat(SYMBOL_MODEL.getScopeFor(f).getTree().is(Tree.Kind.SCRIPT)).isTrue();
    assertThat(SYMBOL_MODEL.getScopeFor(e).getTree().is(Tree.Kind.CATCH_BLOCK)).isTrue();

  }



}
