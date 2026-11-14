const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
test('completion and definition use Java symbols', async () => {
    const kinds = {Class: 4, Interface: 10, Enum: 9, Field: 7, Property: 6, Constant: 13, Method: 5, Function: 11};
    let completion, definition;
    const fake = {
        SymbolKind: kinds, CompletionItemKind: {Field: 1, Method: 2},
        CompletionItem: class { constructor(label, kind) { this.label = label; this.kind = kind; } },
        Range: class { constructor(start, end) { this.start = start; this.end = end; } },
        Location: class { constructor(uri, range) { this.uri = uri; this.range = range; } },
        languages: {
            registerCompletionItemProvider: (_, provider) => (completion = provider, {}),
            registerDefinitionProvider: (_, provider) => (definition = provider, {})
        },
        commands: { executeCommand: async (command) => command === 'vscode.executeWorkspaceSymbolProvider'
            ? [{name: 'Target', kind: kinds.Class, containerName: 'demo', location: {uri: 'target.java'}}]
            : [{name: 'Target', kind: kinds.Class, children: [{name: 'tick()', kind: kinds.Method, selectionRange: 'method-range'}]}] }
    };
    const original = Module._load;
    Module._load = function(name, ...args) { return name === 'vscode' ? fake : original.call(this, name, ...args); };
    try { require('../src/extension').activate({subscriptions: []}); } finally { Module._load = original; }
    const text = 'package demo; @Mixin(Target.class) class M { @Inject(method = "tick") void run() {} }';
    const doc = {getText: () => text, offsetAt: x => x, positionAt: x => x};
    const pos = text.indexOf('tick') + 2, token = {isCancellationRequested: false};
    assert.equal((await completion.provideCompletionItems(doc, pos, token))[0].label, 'tick');
    const locations = await definition.provideDefinition(doc, pos, token);
    assert.equal(locations[0].uri, 'target.java');
    assert.equal(locations[0].range, 'method-range');
});
