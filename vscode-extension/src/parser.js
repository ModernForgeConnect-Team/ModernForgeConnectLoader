'use strict';

// Keep offsets intact while hiding comments. String contents are kept for annotation parsing.
function withoutComments(text) {
    return text.replace(/("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,
        (match, quoted) => quoted || match.replace(/[^\r\n]/g, ' '));
}

function balanced(text, start, open, close) {
    let depth = 0, quote = false, escape = false;
    for (let i = start; i < text.length; i++) {
        const c = text[i];
        if (quote) {
            if (escape) escape = false;
            else if (c === '\\') escape = true;
            else if (c === '"') quote = false;
        } else if (c === '"') quote = true;
        else if (c === open) depth++;
        else if (c === close && --depth === 0) return i;
    }
    return -1;
}

function annotations(text) {
    const result = [], re = /@([\w.]+)\s*\(/g;
    let match;
    while ((match = re.exec(text))) {
        const start = re.lastIndex - 1, end = balanced(text, start, '(', ')');
        if (end < 0) continue;
        result.push({name: match[1].split('.').pop(), start: match.index, bodyStart: start + 1, end, body: text.slice(start + 1, end)});
        // Do not skip the body: nested annotations may also contain references.
    }
    return result;
}

function context(source, offset) {
    const text = withoutComments(source), anns = annotations(text);
    const current = anns.filter(a => a.bodyStart <= offset && offset <= a.end && /^(LegacyMethod|LegacyField|Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant)$/.test(a.name)).pop();
    if (!current) return null;
    const prefix = text.slice(current.bodyStart, offset);
    // Locate a string literal belonging to the relevant named argument, including arrays.
    const strings = /"(?:\\.|[^"\\])*"/g;
    let s, literal;
    while ((s = strings.exec(current.body))) {
        const start = current.bodyStart + s.index;
        if (start < offset && offset <= start + s[0].length - 1) { literal = {start, raw: s[0].slice(1, -1)}; break; }
    }
    if (!literal || literal.raw.includes('\\') || /^L[^;]*;/.test(literal.raw)) return null;
    const before = text.slice(current.bodyStart, literal.start);
    const arg = [...before.matchAll(/\b(name|method|\w+)\s*=/g)].pop();
    if (!arg || arg[1] !== (current.name.startsWith('Legacy') ? 'name' : 'method')) return null;
    // Find the innermost class containing the annotation.
    const classRe = /\b(?:class|interface|enum|record)\s+(\w+)[^{;]*\{/g;
    let m, owner;
    while ((m = classRe.exec(text)) && m.index < current.start) {
        const open = classRe.lastIndex - 1, end = balanced(text, open, '{', '}');
        if (end >= current.end) owner = {start: m.index, open, end};
    }
    if (!owner) return null;
    const targetName = current.name.startsWith('Legacy') ? 'LegacyType' : 'Mixin';
    const target = anns.filter(a => a.name === targetName && a.end < owner.start && !/[;{}]/.test(text.slice(a.end + 1, owner.start))).pop();
    if (!target) return null;
    const targets = [];
    const targetRe = /(?:\b([\w.$]+)\s*\.class\b)|"([^"\\]+)"/g;
    while ((m = targetRe.exec(target.body))) targets.push((m[1] || m[2]).replace(/\$/g, '.'));
    if (!targets.length) return null;
    const end = literal.raw.search(/[(:]/);
    const member = end < 0 ? literal.raw : literal.raw.slice(0, end);
    if (offset > literal.start + 1 + member.length) return null;
    const imports = [...text.matchAll(/\bimport\s+([\w.]+)\s*;/g)].map(x => x[1]);
    const pkg = /\bpackage\s+([\w.]+)\s*;/.exec(text)?.[1] || '';
    return {targets: targets.map(t => imports.find(i => i.endsWith('.' + t)) || (t.includes('.') ? t : (pkg ? pkg + '.' : '') + t)),
        member, field: current.name === 'LegacyField', start: literal.start + 1, end: literal.start + 1 + member.length};
}
module.exports = { context };
