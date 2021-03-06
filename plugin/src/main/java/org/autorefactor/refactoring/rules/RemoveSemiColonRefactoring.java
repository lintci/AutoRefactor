/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2015 Jean-Noël Rouvignac - initial API and implementation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program under LICENSE-GNUGPL.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution under LICENSE-ECLIPSE, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.autorefactor.refactoring.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.autorefactor.refactoring.ASTHelper.NodeStartPositionComparator;
import org.autorefactor.refactoring.SourceLocation;
import org.autorefactor.util.NotImplementedException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import static org.autorefactor.refactoring.ASTHelper.*;
import static org.autorefactor.refactoring.SourceLocation.*;

/**
 * See {@link #getDescription()} method.
 * <p>
 * TODO remove superfluous semi-colons in try-with-resources
 */
@SuppressWarnings("javadoc")
public class RemoveSemiColonRefactoring extends AbstractRefactoringRule {

    @Override
    public String getDescription() {
        return "Removes superfluous semi-colon after body declarations in type declarations.";
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        return visit((BodyDeclaration) node);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        return visit((BodyDeclaration) node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        return visit((BodyDeclaration) node);
    }

    @Override
    public boolean visit(Initializer node) {
        return visit((BodyDeclaration) node);
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        return visit((BodyDeclaration) node);
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        return visit((BodyDeclaration) node);
    }

    private boolean visit(BodyDeclaration node) {
        final BodyDeclaration nextSibling = getNextSibling(node);
        final ASTNode parent = node.getParent();
        if (nextSibling != null) {
            return removeSuperfluousCommas(node, getEndPosition(node), nextSibling.getStartPosition());
        } else if (parent instanceof TypeDeclaration) {
            final TypeDeclaration typeDecl = (TypeDeclaration) parent;
            return removeSuperfluousCommas(node, getEndPosition(node), getEndPosition(typeDecl) - 1);
        } else if (parent instanceof CompilationUnit) {
            final CompilationUnit cu = (CompilationUnit) parent;
            return removeSuperfluousCommas(node, getEndPosition(node), getEndPosition(cu) - 1);
        }
        throw new NotImplementedException(node,
                "for a parent of type " + (parent != null ? parent.getClass().getSimpleName() : null));
    }

    private boolean removeSuperfluousCommas(ASTNode node, int start, int end) {
        boolean result = VISIT_SUBTREE;
        final String source = ctx.getSource(node);
        final ASTNode root = node.getRoot();
        if (root instanceof CompilationUnit) {
            final CompilationUnit cu = (CompilationUnit) root;
            final List<Comment> comments = filterCommentsInRange(start, end, getCommentList(cu));
            final Map<String, SourceLocation> nonCommentsStrings = getNonCommentsStrings(source, start, end, comments);
            for (Entry<String, SourceLocation> entry : nonCommentsStrings.entrySet()) {
                final String s = entry.getKey();
                final Matcher m = Pattern.compile("\\s*(;+)\\s*").matcher(s);
                while (m.find()) {
                    int startPos = entry.getValue().getStartPosition();
                    SourceLocation toRemove = fromPositions(startPos + m.start(1), startPos + m.end(1));
                    this.ctx.getRefactorings().remove(toRemove);
                    result = DO_NOT_VISIT_SUBTREE;
                }
            }
        }
        return result;
    }

    private Map<String, SourceLocation> getNonCommentsStrings(
            String source, int start, int end, List<Comment> comments) {
        final LinkedHashMap<String, SourceLocation> results = new LinkedHashMap<String, SourceLocation>();
        if (comments.isEmpty()) {
            putResult(source, start, end, results);
        } else {
            int nextStart = start;
            for (Comment comment : comments) {
                if (nextStart < comment.getStartPosition()) {
                    putResult(source, nextStart, comment.getStartPosition(), results);
                }
                nextStart = getEndPosition(comment);
            }
        }
        return results;
    }

    private void putResult(String source, int start, int end,
            final LinkedHashMap<String, SourceLocation> results) {
        final SourceLocation loc = fromPositions(start, end);
        final String s = source.substring(loc.getStartPosition(), loc.getEndPosition());
        results.put(s, loc);
    }

    private List<Comment> filterCommentsInRange(int start, int end, List<Comment> commentList) {
        if (commentList.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Comment> comments = new ArrayList<Comment>(commentList);
        Collections.sort(comments, new NodeStartPositionComparator());

        final Iterator<Comment> it = comments.iterator();
        while (it.hasNext()) {
            final Comment comment = it.next();
            if (comment.getStartPosition() < start
                    || getEndPosition(comment) > end) {
                it.remove();
            }
        }
        return comments;
    }
}
