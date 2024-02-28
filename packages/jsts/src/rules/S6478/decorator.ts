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
// https://sonarsource.github.io/rspec/#/rspec/S6478/javascript

import { Rule } from 'eslint';
import * as estree from 'estree';
import { RuleContext, functionLike, interceptReportForReact } from '../helpers';
import { getMainFunctionTokenLocation } from 'eslint-plugin-sonarjs/lib/utils/locations';
import { TSESTree } from '@typescript-eslint/utils';

export function decorate(rule: Rule.RuleModule): Rule.RuleModule {
  return interceptReportForReact(rule, (context, report) => {
    const message =
      'Move this component definition out of the parent component and pass data as props.';
    const { node } = report as { node: estree.Node };
    const loc = getMainNodeLocation(node, context);
    if (loc) {
      context.report({ ...report, loc, message });
    } else {
      context.report({ ...report, message });
    }
  });

  function getMainNodeLocation(node: estree.Node, context: Rule.RuleContext) {
    /* class components */
    if (node.type === 'ClassDeclaration' || node.type === 'ClassExpression') {
      if (node.id) {
        return node.id.loc;
      } else {
        return context.sourceCode.getFirstToken(node, token => token.value === 'class')?.loc;
      }
    }

    /* functional components */
    if (functionLike.has(node.type)) {
      const fun = node as unknown as TSESTree.FunctionLike;
      const ctx = context as unknown as RuleContext;
      return getMainFunctionTokenLocation(fun, fun.parent, ctx) as estree.SourceLocation;
    }

    /* should not happen */
    return node.loc;
  }
}
