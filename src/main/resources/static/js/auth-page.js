/**
 * 登录 / 注册页：每个密码框右侧加「小眼睛」，点击切换明文 / 密文
 */
(function () {
    var SVG = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">';
    var EYE = SVG + '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
    var EYE_OFF = SVG + '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>'
        + '<path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>'
        + '<path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';

    var inputs = document.querySelectorAll('input[type="password"]');
    Array.prototype.forEach.call(inputs, function (input) {
        var wrap = document.createElement('div');
        wrap.className = 'pwd-field';
        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);

        var toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'pwd-toggle';
        toggle.tabIndex = -1;   // Tab 键仍在输入框之间跳转
        toggle.setAttribute('aria-label', '显示密码');
        toggle.innerHTML = EYE;
        toggle.addEventListener('click', function () {
            var show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            toggle.innerHTML = show ? EYE_OFF : EYE;
            toggle.setAttribute('aria-label', show ? '隐藏密码' : '显示密码');
            input.focus();
        });
        wrap.appendChild(toggle);
    });
})();
