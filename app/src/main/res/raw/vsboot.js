if(!window.vscodeOrigKeyboardEventDescriptorCode) window.vscodeOrigKeyboardEventDescriptorCode = Object.getOwnPropertyDescriptor(window.KeyboardEvent.prototype, 'code');
var codeGetter = window.vscodeOrigKeyboardEventDescriptorCode.get;
// if(!window.vscodeOrigKeyboardEventDescriptorShift) window.vscodeOrigKeyboardEventDescriptorShift = Object.getOwnPropertyDescriptor(KeyboardEvent.prototype, 'shiftKey');
// var shiftGetter = window.vscodeOrigKeyboardEventDescriptorShift.get;
// if(!window.vscodeOrigKeyboardEventDescriptorAlt) window.vscodeOrigKeyboardEventDescriptorAlt = Object.getOwnPropertyDescriptor(KeyboardEvent.prototype, 'altKey');
// var altGetter = window.vscodeOrigKeyboardEventDescriptorAlt.get;

var customMapper = {
    27: 'Escape',
    9: 'Tab'
};
var customKeyRemapper = {
    'Backspace': 'Backspace',
    'Control': 'ControlLeft',
    'Alt': 'AltLeft',
};
var prepareCustomProps = function() {
    if(this._vscode_modded) return;
    this._vscode_modded = true;
    var orig = codeGetter.apply(this);
    if (this.key == 'Alt') {
        this._vscodeCode = "";
    } else if (this.key === 'Shift') {
        this._vscodeCode = "ShiftLeft";
    } else if (this.key.match(/^F[0-9]+$/)) {
        this._vscodeCode = this.key;
    } else if (this.which in customMapper) {
        this._vscodeCode = customMapper[this.which];
    } else if (orig === "" && typeof this.key === "string" && this.key.length) {
        if (this.key in customKeyRemapper) this._vscodeCode = customKeyRemapper[this.key];
        else if(this.key>="a" && this.key<="z") this._vscodeCode = "Key" + this.key.toUpperCase();
        else if(this.key>="A" && this.key<="Z") this._vscodeCode = "Key" + this.key;
    } else this._vscodeCode = orig;
}
Object.defineProperty(window.KeyboardEvent.prototype, 'code', {
    get(){
        prepareCustomProps.apply(this);
        return this._vscodeCode;
    }
});
//Object.defineProperty(window.KeyboardEvent.prototype, 'altKey', {
//    get(){
//        if (!altGetter.apply(this)) return false;
//        prepareCustomProps.apply(this);
//        return this.code === "";
//    }
//});

//navigator.clipboard.read
