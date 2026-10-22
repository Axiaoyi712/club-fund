(() => {
    const body = document.querySelector('#itemTbody');
    const currency = cents => Number.isSafeInteger(cents) ? '¥' + (cents / 100).toFixed(2) : '—';
    function toCents(input) {
        const [mantissa, exponent = '0'] = input.value.toLowerCase().split('e');
        const places = (mantissa.split('.')[1] || '').length - Number(exponent);
        const precise = places <= 2;
        input.setCustomValidity(precise ? '' : '金额最多两位小数，请修改');
        const value = Number(input.value || 0);
        const cents = Math.round(value * 100);
        return precise && value >= 0 && Number.isSafeInteger(cents) ? cents : null;
    }
    function refreshRows() {
        let budgetTotal = 0, applyTotal = 0;
        body.querySelectorAll('tr').forEach((row, index) => {
            row.querySelector('.seq').textContent = index + 1;
            const fields = {'.item-name':'itemName', '.qty':'quantity', '.price':'unitPrice', '.apply':'applyAmount'};
            Object.entries(fields).forEach(([selector, field]) => {
                const input = row.querySelector(selector);
                input.name = `items[${index}].${field}`;
                input.setAttribute('aria-label', `第${index + 1}行${{'itemName':'开支项目','quantity':'数量','unitPrice':'单价','applyAmount':'申请金额'}[field]}`);
            });
            const quantity = Number(row.querySelector('.qty').value);
            const price = toCents(row.querySelector('.price'));
            const applyInput = row.querySelector('.apply');
            const apply = toCents(applyInput);
            const budget = Number.isInteger(quantity) && quantity > 0 && price !== null ? quantity * price : null;
            const overBudget = Number.isSafeInteger(budget) && apply !== null && apply > budget;
            const error = row.querySelector('.apply-error');
            error.id = `apply-error-${index}`;
            applyInput.setAttribute('aria-describedby', error.id);
            const message = overBudget ? `申请金额不能超过该项预算（${currency(budget)}）` : '';
            if (overBudget) applyInput.setCustomValidity(message);
            applyInput.setAttribute('aria-invalid', String(!applyInput.validity.valid));
            error.textContent = message;
            error.hidden = !overBudget;
            row.querySelector('.budget').textContent = currency(budget);
            budgetTotal = budgetTotal === null || budget === null ? null : budgetTotal + budget;
            applyTotal = applyTotal === null || apply === null ? null : applyTotal + apply;
        });
        document.querySelector('#totalBudget').textContent = currency(budgetTotal);
        document.querySelector('#totalApply').textContent = currency(applyTotal);
    }
    function addRow() {
        const row = document.createElement('tr');
        row.innerHTML = '<td class="seq"></td><td><input class="form-control form-control-sm item-name" required maxlength="100" placeholder="如：活动物资"></td><td><input type="number" min="1" step="1" max="2147483647" value="1" class="form-control form-control-sm qty" required></td><td><input type="number" min="0" step="0.01" value="0" class="form-control form-control-sm price" max="99999999.99" required></td><td class="budget text-end">¥0.00</td><td><input type="number" min="0" step="0.01" value="0" class="form-control form-control-sm apply" max="99999999.99"><div class="field-error apply-error" aria-live="polite" hidden></div></td><td><button type="button" class="btn btn-outline-danger btn-sm remove-item">删除</button></td>';
        body.appendChild(row);
        refreshRows();
        return row;
    }
    document.querySelector('#add-item').addEventListener('click', () => addRow().querySelector('input').focus());
    body.addEventListener('input', refreshRows);
    body.addEventListener('click', event => {
        if (!event.target.closest('.remove-item')) return;
        event.target.closest('tr').remove();
        if (!body.children.length) addRow();
        refreshRows();
    });
    const form = document.querySelector('#activity-form');
    const submitButton = form.querySelector('button[type="submit"]');
    let submitting = false;
    form.addEventListener('submit', event => {
        if (submitting) {
            event.preventDefault();
            return;
        }
        submitting = true;
        submitButton.disabled = true;
    });
    window.addEventListener('pageshow', () => {
        submitting = false;
        submitButton.disabled = false;
    });
    if (!body.children.length) addRow();
    refreshRows();
})();
