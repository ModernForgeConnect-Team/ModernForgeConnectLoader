'use strict';
const vscode = require('vscode');
const { context } = require('./parser');

async function members(ctx, token) {
    const results = [];
    for (const target of ctx.targets) {
        if (token.isCancellationRequested) break;
        const simple = target.split('.').pop();
        const symbols = await vscode.commands.executeCommand('vscode.executeWorkspaceSymbolProvider', simple) || [];
        const classes = symbols.filter(s => s.name === simple && [vscode.SymbolKind.Class, vscode.SymbolKind.Interface, vscode.SymbolKind.Enum].includes(s.kind));
        // Prefer a matching package or enclosing type when the provider supplies one.
        classes.sort ( (a, b) => Number(target.includes(b.containerName || '\u0000')) - Number(target.includes(a.containerName || '\u0000')) );
        const candidate = classes[0];
        if (!candidate) continue;
        let tree;
        try { tree = await vscode.commands.executeCommand('vscode.executeDocumentSymbolProvider', candidate.location.uri); }
        catch (_) { continue; }
        if (!tree) continue;
        function visit(items, inTarget) {
            for (const item of items) {
                const selected = inTarget || (item.name === simple && [vscode.SymbolKind.Class, vscode.SymbolKind.Interface, vscode.SymbolKind.Enum].includes(item.kind));
                const wanted = ctx.field ? [vscode.SymbolKind.Field, vscode.SymbolKind.Property, vscode.SymbolKind.Constant] : [vscode.SymbolKind.Method, vscode.SymbolKind.Function];
                if (inTarget && wanted.includes(item.kind)) {
                    results.push({name: item.name.replace(/\(.*/, ''), detail: item.detail, location: new vscode.Location(candidate.location.uri, item.selectionRange || item.range || item.location.range)});
                }
                if (item.children && !(inTarget && selected && [vscode.SymbolKind.Class, vscode.SymbolKind.Interface, vscode.SymbolKind.Enum].includes(item.kind))) visit(item.children, selected);
            }
        }
        visit(tree, false);
    }
    return results;
}

function activate(context) {
    const selector = { language: 'java', scheme: 'file' };
    context.subscriptions.push(vscode.languages.registerCompletionItemProvider(selector, {
        async provideCompletionItems(document, position, token) {
            const ctx = contextAt(document, position);
            if (!ctx) return;
            const found = await members(ctx, token), seen = new Set();
            return found.filter(m => !seen.has(m.name) && seen.add(m.name)).map(m => {
                const item = new vscode.CompletionItem(m.name, ctx.field ? vscode.CompletionItemKind.Field : vscode.CompletionItemKind.Method);
                item.range = new vscode.Range(document.positionAt(ctx.start), document.positionAt(ctx.end));
                item.detail = m.detail;
                return item;
            });
        }
    }, '"'));
    context.subscriptions.push(vscode.languages.registerDefinitionProvider(selector, {
        async provideDefinition(document, position, token) {
            const ctx = contextAt(document, position);
            if (!ctx || !ctx.member) return;
            return (await members(ctx, token)).filter(m => m.name === ctx.member).map(m => m.location);
        }
    }));
}
function contextAt(document, position) { return context(document.getText(), document.offsetAt(position)); }
module.exports = { activate };
