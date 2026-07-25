package org.example.openccjavafx.ui;

import org.fxmisc.richtext.CodeArea;

/** Shared RichTextFX navigation for known-valid one-based logical lines. */
public final class EditorNavigationHelper {
    private EditorNavigationHelper() {
    }

    public static void goToLine(CodeArea editor, int oneBasedLine, boolean focusEditor) {
        if (editor == null) {
            throw new IllegalArgumentException("editor must not be null");
        }
        int paragraphCount = editor.getParagraphs().size();
        if (oneBasedLine < 1 || oneBasedLine > paragraphCount) {
            throw new IllegalArgumentException(
                    "line must be between 1 and " + Math.max(1, paragraphCount));
        }

        int paragraphIndex = oneBasedLine - 1;
        editor.moveTo(paragraphIndex, 0);
        editor.showParagraphAtCenter(paragraphIndex);
        editor.requestFollowCaret();
        if (focusEditor) {
            editor.requestFocus();
        }
    }
}
