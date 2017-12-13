/**
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import React from 'react';
import PropTypes from 'prop-types';
import log from 'log';
import _ from 'lodash';
import debuggerHoc from 'src/plugins/debugger/views/DebuggerHoc';
import File from 'core/workspace/model/file';
import { CONTENT_MODIFIED } from 'plugins/ballerina/constants/events';
import { GO_TO_POSITION } from 'plugins/ballerina/constants/commands';
import MonacoEditor from 'react-monaco-editor';
import { getLangServerClientInstance } from 'plugins/ballerina/langserver/lang-server-client-controller';
import SourceViewCompleterFactory from './../../ballerina/utils/source-view-completer-factory';
import { CHANGE_EVT_TYPES } from './constants';


class SourceEditor extends React.Component {

    constructor(props) {
        super(props);
        this.editor = undefined;
        this.inSilentMode = false;
        this.sourceViewCompleterFactory = new SourceViewCompleterFactory();
        this.goToCursorPosition = this.goToCursorPosition.bind(this);
        this.onFileContentChanged = this.onFileContentChanged.bind(this);
        this.lastUpdatedTimestamp = props.file.lastUpdated;
    }

    /**
     * lifecycle hook for component did mount
     */
    componentDidMount() {
    }

    /**
     * Event handler when the content of the file object is changed.
     * @param {Object} evt The event object.
     * @memberof SourceEditor
     */
    onFileContentChanged(evt) {
        if (evt.originEvt.type !== CHANGE_EVT_TYPES.SOURCE_MODIFIED) {
            // no need to update the file again, hence
            // the second arg to skip update event
            this.replaceContent(evt.newContent, true);
        }
    }

    /**
     * Go to given position command handler.
     *
     * @param {Object} args
     * @param {File} args.file File
     * @param {number} args.row Line number
     * @param {number} args.column Offset
     */
    handleGoToPosition(args) {
        if (this.props.file.id === args.file.id) {
            this.goToCursorPosition(args.row, args.column);
        }
    }

    /**
     * Set cursor of the source editor to a
     * specific position.
     *
     * @param {number} row Line Number
     * @param {number} column Offset
     */
    goToCursorPosition(row, column) {
        this.editor.focus();
        this.editor.gotoLine(row + 1, column);
    }

    /**
     * Replace content of the editor while maintaining history
     *
     * @param {*} newContent content to insert
     */
    replaceContent(newContent, skipFileUpdate) {
        if (skipFileUpdate) {
            this.skipFileUpdate = true;
        }
        const session = this.editor.getSession();
        const contentRange = new Range(0, 0, session.getLength(),
                        session.getRowLength(session.getLength()));
        session.replace(contentRange, newContent);
        this.lastUpdatedTimestamp = this.props.file.lastUpdated;
    }

    /**
     * Binds a shortcut to ace editor so that it will trigger the command on source view upon key press.
     * All the commands registered app's command manager will be bound to source view upon render.
     *
     * @param command {Object}
     * @param command.id {String} Id of the command to dispatch
     * @param command.shortcuts {Object}
     * @param command.shortcuts.mac {Object}
     * @param command.shortcuts.mac.key {String} key combination for mac platform eg. 'Command+N'
     * @param command.shortcuts.other {Object}
     * @param command.shortcuts.other.key {String} key combination for other platforms eg. 'Ctrl+N'
     */
    bindCommand(command) {
        const { id, argTypes, shortcut } = command;
        const { dispatch } = this.props.commandProxy;
        if (shortcut) {
            const shortcutKey = _.replace(shortcut.derived.key, '+', '-');
            this.editor.commands.addCommand({
                name: id,
                bindKey: { win: shortcutKey, mac: shortcutKey },
                exec() {
                    dispatch(id, argTypes);
                },
            });
        }
    }

    render() {
        return (
            <div className='text-editor bal-source-editor'>
                <MonacoEditor
                    width="800"
                    height="600"
                    language="javascript"
                    theme="vs-dark"
                    value={` etes etrset est est est set est `}
                />
            </div>
        );
    }

    /**
     * lifecycle hook for component will receive props
     */
    componentWillReceiveProps(nextProps) {
        // if (!nextProps.parseFailed) {
        //     getLangServerClientInstance()
        //         .then((langserverClient) => {
        //             // Set source view completer
        //             const sourceViewCompleterFactory = this.sourceViewCompleterFactory;
        //             const fileData = {
        //                 fileName: nextProps.file.name,
        //                 filePath: nextProps.file.path,
        //                 fullPath: nextProps.file.fullPath,
        //                 packageName: nextProps.file.packageName,
        //             };
        //             const completer = sourceViewCompleterFactory.getSourceViewCompleter(langserverClient, fileData);
        //             langTools.setCompleters(completer);
        //         })
        //         .catch(error => log.error(error));
        // }

        // const { debugHit, sourceViewBreakpoints } = nextProps;
        // if (this.debugPointMarker) {
        //     this.editor.getSession().removeMarker(this.debugPointMarker);
        // }
        // if (debugHit > 0) {
        //     this.debugPointMarker = this.editor.getSession().addMarker(
        //         new Range(debugHit, 0, debugHit, 2000), 'debug-point-hit', 'line', true);
        // }

        // if (this.props.file.id !== nextProps.file.id) {
        //     // Removing the file content changed event of the previous file.
        //     this.props.file.off(CONTENT_MODIFIED, this.onFileContentChanged);
        //     // Adding the file content changed event to the new file.
        //     nextProps.file.on(CONTENT_MODIFIED, this.onFileContentChanged);
        //     this.replaceContent(nextProps.file.content, true);
        // } else if (this.editor.session.getValue() !== nextProps.file.content) {
        //     this.replaceContent(nextProps.file.content, true);
        // }

        // this.editor.getSession().setBreakpoints(sourceViewBreakpoints);
    }
}

SourceEditor.propTypes = {
    file: PropTypes.instanceOf(File).isRequired,
    commandProxy: PropTypes.shape({
        on: PropTypes.func.isRequired,
        dispatch: PropTypes.func.isRequired,
        getCommands: PropTypes.func.isRequired,
    }).isRequired,
    parseFailed: PropTypes.bool.isRequired,
    onLintErrors: PropTypes.func,
    sourceViewBreakpoints: PropTypes.arrayOf(Number).isRequired,
    addBreakpoint: PropTypes.func.isRequired,
    removeBreakpoint: PropTypes.func.isRequired,
    debugHit: PropTypes.number,
};

SourceEditor.defaultProps = {
    debugHit: null,
    onLintErrors: () => {},
};

export default debuggerHoc(SourceEditor);
