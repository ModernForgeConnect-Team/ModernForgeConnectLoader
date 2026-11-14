const test = require('node:test');
const assert = require('node:assert/strict');
const { context } = require('../src/parser');
function at(source, needle, delta = 1) { return context(source, source.indexOf(needle) + delta); }
test('legacy accessor resolves imported target and member', () => {
    const s = 'package demo; import old.Block; @LegacyType(type = Block.class) class A { @LegacyField(name = "hardness") int x; }';
    assert.deepEqual(at(s, 'hardness'), {targets: ['old.Block'], member: 'hardness', field: true, start: s.indexOf('hardness'), end: s.indexOf('hardness') + 8});
});
test('mixin method array and descriptor', () => {
    const s = '@Mixin(targets = {"old.Block", "old.Item"}) class A { @Inject(method = {"tick()V", "update"}, at = @At("HEAD")) void x() {} }';
    assert.deepEqual(at(s, 'tick').targets, ['old.Block', 'old.Item']);
    assert.equal(at(s, 'tick').member, 'tick');
    assert.equal(at(s, 'update').member, 'update');
    assert.equal(at(s, 'HEAD'), null);
});
test('ignores unsupported selectors and comments', () => {
    const s = '// @Mixin(Fake.class)\n@Mixin(Real.class) class A { @Inject(method = "Lowner;tick()V") void x() {} }';
    assert.equal(at(s, 'Lowner'), null);
});
