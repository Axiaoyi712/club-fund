(() => {
    const select = document.querySelector('#expense-item');
    if (select) {
        const field = document.querySelector('.other-field');
        const name = document.querySelector('#other-item');
        const count = document.querySelector('#other-item-count');
        const error = document.querySelector('#other-item-error');
        const validateName = () => {
            const length = Array.from(name.value).length;
            count.textContent = `${length}/100字`;
            const message = select.value === 'other' && length > 100 ? '支出项目名称最多100字，请修改后登记。' : '';
            name.setCustomValidity(message);
            name.setAttribute('aria-invalid', String(Boolean(message)));
            error.textContent = message;
            error.hidden = !message;
        };
        const sync = () => {
            const other = select.value === 'other';
            field.hidden = !other;
            name.required = other;
            // “其他支出”不携带预算明细 ID，后端按手填项目名登记。
            if (!other) name.value = '';
            validateName();
        };
        name.addEventListener('input', validateName);
        select.addEventListener('change', sync);
        select.form.addEventListener('submit', () => {
            if (select.value === 'other') select.disabled = true;
        });
        window.addEventListener('pageshow', () => { select.disabled = false; sync(); });
        sync();
        if (!error.hidden) name.focus();
    }
    const expenseForm = document.querySelector('.expense-entry');
    if (expenseForm) {
        const button = expenseForm.querySelector('button[type="submit"]');
        const label = button.textContent;
        let submitting = false;
        expenseForm.addEventListener('submit', event => {
            if (submitting) {
                event.preventDefault();
                return;
            }
            if (event.defaultPrevented) return;
            submitting = true;
            button.disabled = true;
            button.textContent = '登记中…';
            button.setAttribute('aria-busy', 'true');
        });
        window.addEventListener('pageshow', () => {
            submitting = false;
            button.disabled = false;
            button.textContent = label;
            button.removeAttribute('aria-busy');
        });
    }
    const amount = document.querySelector('#expense-amount');
    if (amount) {
        const validateAmount = () => {
            const [mantissa, exponent = '0'] = amount.value.toLowerCase().split('e');
            const places = (mantissa.split('.')[1] || '').length - Number(exponent);
            amount.setCustomValidity(places > 2 ? '金额最多两位小数，请修改' : '');
        };
        amount.addEventListener('input', validateAmount);
        validateAmount();
    }
    const note = document.querySelector('#expense-note');
    if (note) {
        const count = document.querySelector('#expense-note-count');
        const error = document.querySelector('#expense-note-error');
        const updateNote = () => {
            const length = Array.from(note.value).length;
            count.textContent = `${length}/200字`;
            const message = length > 200 ? '备注最多200字，请修改后登记。' : '';
            note.setCustomValidity(message);
            note.setAttribute('aria-invalid', String(Boolean(message)));
            error.textContent = message;
            error.hidden = !message;
        };
        note.addEventListener('input', updateNote);
        updateNote();
        if (!error.hidden) note.focus();
    }
    const form = document.querySelector('#approval-form');
    if (form) {
        const comment = document.querySelector('#approval-comment');
        const error = document.querySelector('#approval-error');
        const count = document.querySelector('#approval-comment-count');
        const showError = message => {
            error.textContent = message;
            error.hidden = false;
            comment.setAttribute('aria-invalid', 'true');
        };
        const updateCount = () => {
            const length = Array.from(comment.value).length;
            count.textContent = `${length}/500字`;
            const message = length > 500 ? '审批意见最多500字，请修改后提交。' : '';
            comment.setCustomValidity(message);
            if (message) showError(message);
            return length;
        };
        comment.addEventListener('input', () => {
            error.hidden = true;
            comment.removeAttribute('aria-invalid');
            updateCount();
        });
        updateCount();
        if (!error.hidden) {
            comment.setAttribute('aria-invalid', 'true');
            comment.focus();
        }
        form.addEventListener('submit', event => {
            const rejecting = event.submitter?.value === 'reject';
            const message = updateCount() > 500 ? '审批意见最多500字，请修改后提交。'
                : rejecting && !comment.value.trim() ? '请填写驳回原因，便于申请人核对。' : '';
            if (message) {
                event.preventDefault();
                showError(message);
                comment.focus();
                return;
            }
            error.hidden = true;
            comment.removeAttribute('aria-invalid');
            if (rejecting && !window.confirm('确定驳回这份申请吗？')) event.preventDefault();
        });
    }
})();
