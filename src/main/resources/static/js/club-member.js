(() => {
    const name = document.querySelector('#member-name');
    if (!name) return;
    const count = document.querySelector('#member-name-count');
    const error = document.querySelector('#member-name-error');
    const validateName = () => {
        const length = Array.from(name.value).length;
        count.textContent = `${length}/50字`;
        const message = length > 50 ? '成员姓名最多50字，请修改后添加。' : '';
        name.setCustomValidity(message);
        name.setAttribute('aria-invalid', String(Boolean(message)));
        error.textContent = message;
        error.hidden = !message;
    };
    const form = name.form;
    const button = form.querySelector('button[type="submit"]');
    const label = button.textContent;
    let submitting = false;
    form.addEventListener('submit', event => {
        if (submitting) {
            event.preventDefault();
            return;
        }
        if (event.defaultPrevented) return;
        submitting = true;
        button.disabled = true;
        button.textContent = '添加中…';
        button.setAttribute('aria-busy', 'true');
    });
    name.addEventListener('input', validateName);
    window.addEventListener('pageshow', () => {
        submitting = false;
        button.disabled = false;
        button.textContent = label;
        button.removeAttribute('aria-busy');
        validateName();
    });
    validateName();
    if (!error.hidden) name.focus();
})();
