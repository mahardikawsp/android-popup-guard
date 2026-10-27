const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
class Element {
  constructor(attributes = {}) { this.attributes = attributes; }
  getAttribute(name) { return this.attributes[name] ?? null; }
}
class Anchor extends Element {}
class Area extends Element {}
class Form extends Element { submit() { this.submitted = true; } }
const listeners = {};
const window = {addEventListener(name, fn) { listeners[name] = fn; }};
let base = null;
vm.runInNewContext(readFileSync(require('node:path').join(__dirname, '../app/src/main/assets/guard.js'), 'utf8'), {
  window, document: {querySelector() { return base; }},
  HTMLAnchorElement: Anchor, HTMLAreaElement: Area, HTMLFormElement: Form,
  console: {info() {}}
});
function click(link, overrides = {}) {
  const event = {
    button: 0, composedPath: () => [link], preventDefault() { this.blocked = true; },
    stopImmediatePropagation() { this.stopped = true; }, ...overrides
  };
  listeners.click(event);
  return event;
}
assert.equal(window.open('https://example.com'), null);
assert.equal(Object.getOwnPropertyDescriptor(window, 'open').writable, false);
assert.equal(click(new Anchor({target: '_blank'})).blocked, true);
assert.equal(click(new Anchor({target: 'ad'})).blocked, true);
assert.equal(click(new Anchor({target: '_self'})).blocked, undefined);
assert.equal(click(new Anchor()).blocked, undefined);
assert.equal(click(new Anchor(), {metaKey: true}).blocked, true);
assert.equal(click(new Anchor(), {button: 1}).blocked, true);
assert.equal(click({}).blocked, undefined); // A video click must be unaffected.
base = new Element({target: '_blank'});
assert.equal(click(new Anchor()).blocked, true);
assert.equal(click(new Anchor({target: ''})).blocked, undefined);
base = null;
const blockedForm = new Form({target: '_blank'});
blockedForm.submit();
assert.equal(blockedForm.submitted, undefined);
const normalForm = new Form();
normalForm.submit();
assert.equal(normalForm.submitted, true);
let event = {target: normalForm, submitter: new Element({formtarget: '_blank'}),
  preventDefault() { this.blocked = true; }, stopImmediatePropagation() {}};
listeners.submit(event);
assert.equal(event.blocked, true);
console.log('PASS: window.open, named/blank links, modifier clicks, base target, forms, normal/video clicks.');
