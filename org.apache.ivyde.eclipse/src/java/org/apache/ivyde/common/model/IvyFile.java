/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivyde.common.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class IvyFile {
    private static final Pattern ATTRIBUTE_NAME_PATTERN = Pattern
            .compile("[^\"]*\"[\\s]*=[\\s]*([\\w\\-]+)");

    private static final Pattern QUALIFIER_PATTERN = Pattern.compile("[\\w\\-<]*");

    private static final Pattern ATTRIBUTE_VALUE_PATTERN = Pattern
            .compile("([a-zA-Z0-9]+)[ ]*=[ ]*\"([^\"]*)\"");

    private String _doc;

    private int _currentOffset;

    private String _reversed;

    private String _projectName;

    private IvyModelSettings settings;

    public IvyFile(IvyModelSettings settings, String projectName, String doc) {
        this(settings, projectName, doc, 0);
    }

    public IvyFile(IvyModelSettings settings, String projectName, String doc, int currentOffset) {
        this.settings = settings;
        _projectName = projectName;
        _doc = doc;
        _reversed = new StringBuffer(doc).reverse().toString();
        _currentOffset = currentOffset;
    }

    protected String getDoc() {
        return _doc;
    }
    
    protected int getCurrentOffset() {
        return _currentOffset;
    }
    
    protected String getReversedDoc() {
        return _reversed;
    }

    public boolean inTag() {
        return inTag(_currentOffset);
    }

    public boolean inTag(int documentOffset) {
        int lastSpaceIndex = documentOffset;
        boolean hasSpace = false;
        while (true) {
            // Read character backwards
            if (documentOffset == 0) {
                return false;
            }
            char c = _doc.charAt(--documentOffset);
            if (Character.isWhitespace(c))
                hasSpace = true;
            if (c == '>' && (documentOffset == 0 || _doc.charAt(documentOffset - 1) != '-'))
                return false;
            if (c == '<'
                    && (documentOffset + 1 >= _doc.length() || (_doc.charAt(documentOffset + 1) != '!' && _doc
                            .charAt(documentOffset + 1) != '?')))
                return hasSpace;
        }
    }

    public String getTagName() {
        return getTagName(_currentOffset);
    }

    /**
     * Return the tag for the position. Note : the documentoffset is considered to be in a tag ie in
     * &lt; &gt;
     * 
     * @param documentOffset
     * @return
     */
    public String getTagName(int documentOffset) {
        int offset = documentOffset;
        int lastSpaceIndex = offset;
        while (true) {
            // Read character backwards
            char c = _doc.charAt(--offset);
            if (Character.isWhitespace(c)) {
                lastSpaceIndex = offset;
                continue;
            }
            if (c == '<')
                return _doc.substring(offset + 1, lastSpaceIndex).trim();
        }
    }

    public boolean readyForValue() {
        return readyForValue(_currentOffset);
    }

    public boolean readyForValue(int documentOffset) {
        return getAttributeName(documentOffset) != null;
    }

    public int getStringIndexBackward(String string) {
        return getStringIndexBackward(string, _currentOffset);
    }

    public int getStringIndexBackward(String string, int documentOffset) {
        String text = _doc.substring(0, documentOffset);
        return text.lastIndexOf(string);
    }

    public int getStringIndexForward(String string) {
        return getStringIndexForward(string, _currentOffset);
    }

    public int getStringIndexForward(String string, int documentOffset) {
        return _doc.indexOf(string, documentOffset);
    }

    public Map getAllAttsValues() {
        return getAllAttsValues(_currentOffset);
    }

    public Map getAllAttsValues(int documentOffset) {
        Map result = new HashMap();

        int offset = documentOffset;
        int start = _reversed.indexOf('<', getReverseOffset(documentOffset));
        if (start != -1) {
            start = getReverseOffset(start);
        } else {
            start = 0;
        }
        int end;
        if (_doc.charAt(documentOffset) == '>' && getAttributeName(documentOffset) == null) {
            end = documentOffset + 1;
        } else {
            Pattern p = Pattern.compile("[^\\-]>");
            Matcher m = p.matcher(_doc);
            if (m.find(documentOffset)) {
                end = m.end();
            } else {
                end = _doc.length();
            }
        }
        Pattern regexp = ATTRIBUTE_VALUE_PATTERN;
        try {
            String tag = _doc.substring(start, end);
            tag = tag.substring(tag.indexOf(' '));
            Matcher m = regexp.matcher(tag);
            while (m.find()) {
                String key = m.group(1);
                String val = m.group(2);
                result.put(key, val);
                if (m.end() + m.group(0).length() < tag.length()) {
                    tag = tag.substring(m.end());
                    m = regexp.matcher(tag);
                }
            }
        } catch (Exception e) {
            // FIXME : what is really catched here ?
            if (settings != null) {
                settings.logError("Something bad happened", e);
            }
        }
        return result;
    }

    public String getQualifier() {
        return getQualifier(_currentOffset);
    }

    /**
     * Return the user typed string before calling completion stop on:<br>
     * &lt; to match tag,<br/> space to found attribute name<br/>
     * 
     * @param documentOffset
     * @return
     */
    public String getQualifier(int documentOffset) {
        Pattern p = QUALIFIER_PATTERN;
        Matcher m = p.matcher(_reversed);
        if (m.find(getReverseOffset(documentOffset))) {
            return _doc.substring(getReverseOffset(m.end()), documentOffset);
        } else {
            return "";
        }
    }

    public String getAttributeValueQualifier() {
        return getAttributeValueQualifier(_currentOffset);
    }

    /**
     * Return the user typed string before calling completion on attribute value stop on:<br> " to
     * match value for attribute
     * 
     * @param documentOffset
     * @return
     */
    public String getAttributeValueQualifier(int documentOffset) {
        int index = _reversed.indexOf("\"", getReverseOffset(documentOffset));
        if (index == -1) {
            return "";
        } else {
            return _doc.substring(getReverseOffset(index), documentOffset);
        }
    }

    /**
     * Returns the attribute name corresponding to the value currently edited
     * 
     * @return null if current offset is not in an attibute value
     */
    public String getAttributeName() {
        return getAttributeName(_currentOffset);
    }

    public String getAttributeName(int documentOffset) {
        Pattern p = ATTRIBUTE_NAME_PATTERN;
        Matcher m = p.matcher(_reversed.substring(getReverseOffset(documentOffset)));
        if (m.find() && m.start() == 0) {
            String attName = new StringBuffer(m.group(1)).reverse().toString();
            return attName;
        } else {
            return null;
        }
    }

    public String getParentTagName() {
        return getParentTagName(_currentOffset);
    }

    public String getParentTagName(int documentOffset) {
        int[] indexes = getParentTagIndex(documentOffset);
        String foundParent = getString(indexes);
        return foundParent == null ? null : foundParent.trim();
    }

    public String getString(int[] indexes) {
        if (indexes != null) {
            return _doc.substring(indexes[0], indexes[1]);
        } else {
            return null;
        }
    }

    public String getString(int start, int end) {
        return _doc.substring(start, end);
    }

    public int[] getParentTagIndex(int documentOffset) {
        int offset = documentOffset;
        int lastSpaceIndex = offset;
        int parentEndTagIndex = -1;
        boolean parentEndTagReached = false;
        boolean inSimpleTag = false;
        Stack stack = new Stack();
        while (true) {
            try {
                char c = _doc.charAt(--offset);
                if (c == '>' && _doc.charAt(offset - 1) != '-') {
                    if (_doc.charAt(offset - 1) != '/') { // not a simple tag
                        // System.out.println("parentEndTagReached:"+doc.get(documentOffset-15,
                        // 15));
                        parentEndTagReached = true;
                        parentEndTagIndex = offset;
                        lastSpaceIndex = offset;
                        // System.out.println("parentEndTagReached:"+doc.get(documentOffset-15,
                        // 15));
                        continue;
                    } else { // simple tag
                        inSimpleTag = true;
                    }
                } else if (c == '<') {
                    if (inSimpleTag) {// simple tag end
                        inSimpleTag = false;
                    } else if (_doc.charAt(offset + 1) == '/') { // closing tag
                        if (parentEndTagReached) {
                            parentEndTagReached = false;
                            stack.push(_doc.substring(offset + 2, parentEndTagIndex).trim());
                            lastSpaceIndex = offset + 2;
                            continue;
                        }
                    } else {// opening tag
                        if (_doc.charAt(offset + 1) != '!' && _doc.charAt(offset + 1) != '?') {// not
                            // a
                            // doc
                            // tag
                            // or
                            // xml
                            if (!stack.isEmpty()) { // we found the closing tag before
                                String closedName = (String) stack.peek();
                                if (closedName.equalsIgnoreCase(_doc.substring(offset + 1, offset
                                        + 1 + closedName.length()))) {
                                    stack.pop();
                                    continue;
                                }
                            } else {
                                if (parentEndTagReached) {
                                    return new int[] {offset + 1, lastSpaceIndex};
                                }
                            }
                        }
                    }
                } else if (Character.isWhitespace(c)) {
                    lastSpaceIndex = offset;
                    continue;
                }
            } catch (IndexOutOfBoundsException e) {
                // FIXME hu ? need some comments
                if (settings != null) {
                    settings.logError("Something bad happened", e);
                }
                return null;
            }
        }
    }

    private int getReverseOffset(int documentOffset) {
        return _doc.length() - documentOffset;
    }

    public int getOffset() {
        return _currentOffset;
    }

    public int[] getParentTagIndex() {
        return getParentTagIndex(_currentOffset);
    }

    public String getProjectName() {
        return _projectName;
    }
}