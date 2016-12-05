/**
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
define(['lodash', 'log', './conditional-statement'], function (_, log, ConditionalStatement) {

    /**
     * Class for while statement in ballerina.
     * @param condition The condition of an while statement.
     * @param statements The statements list of a while statement.
     * @constructor
     */
    var WhileStatement = function (condition, statements) {
        ConditionalStatement.call(condition, statements);
    };

    WhileStatement.prototype = Object.create(ConditionalStatement.prototype);
    WhileStatement.prototype.constructor = WhileStatement;

    WhileStatement.prototype.accept = function (visitor) {
        var canVisitChildren = visitor.visitWhileStatement(this);

        // Accept all the children of the worker
        if(canVisitChildren) {
            var statements = this.getStatements();
            for (var id in statements) {
                this.statements[id].accept(visitor);
            }
        } else {
            log.info("Disable to visit the Children of while statement.");
        }
    };

    return WhileStatement;
});