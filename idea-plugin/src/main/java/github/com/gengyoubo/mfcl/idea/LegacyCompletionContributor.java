package github.com.gengyoubo.mfcl.idea;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/** Explicit basic-completion provider, including an empty annotation string. */
public final class LegacyCompletionContributor extends CompletionContributor {
    public LegacyCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context,
                                                    @NotNull CompletionResultSet result) {
                var literal = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiLiteralExpression.class, false);
                if (literal == null || !literal.getText().startsWith("\"")) return;
                var spec = LegacyReferenceContributor.spec(literal);
                if (spec == null) return;
                int start = literal.getTextRange().getStartOffset() + 1;
                int end = parameters.getOffset();
                if (end < start) return;
                String text = parameters.getOriginalFile().getText();
                if (end > text.length()) return;
                String prefix = text.substring(start, end);
                if (!prefix.matches("[A-Za-z0-9_$]*")) return;
                var matching = result.withPrefixMatcher(prefix);
                for (Object value : LegacyReferenceContributor.variants(spec))
                    if (value instanceof LookupElement lookup) matching.addElement(lookup);
            }
        });
    }
}
