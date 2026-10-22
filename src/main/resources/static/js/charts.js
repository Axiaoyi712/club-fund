(() => {
    const data = typeof window.cfDashboard === 'string' ? JSON.parse(window.cfDashboard) : window.cfDashboard;
    if (!data) return;
    const number = value => Number(value).toLocaleString('zh-CN', {maximumFractionDigits:2});
    document.querySelectorAll('[data-club-chart]').forEach(chart => {
        chart.replaceChildren();
        const clubs = Object.entries(data.clubAmount || {}).sort((a,b) => Number(b[1]) - Number(a[1]));
        const max = Math.max(0,...clubs.map(([,value]) => Number(value)));
        if (!clubs.length) {
            const empty = document.createElement('p'); empty.className = 'chart-empty'; empty.textContent = '暂无活动申请数据'; chart.appendChild(empty); return;
        }
        const axis = document.createElement('div'); axis.className = 'chart-axis';
        [0,max / 2,max].forEach(value => {const tick = document.createElement('span'); tick.textContent = number(value); axis.appendChild(tick);});
        chart.appendChild(axis);
        clubs.forEach(([name,value],index) => {
            const row = document.createElement('div'); row.className = 'bar-row';
            const label = document.createElement('span'); label.className = 'bar-name'; label.textContent = name;
            const track = document.createElement('div'); track.className = 'bar-track';
            const fill = document.createElement('span'); fill.className = 'bar-fill'; fill.style.width = `${max ? Number(value) / max * 100 : 0}%`; fill.style.opacity = String(Math.max(.5,1 - index * .07));
            const amount = document.createElement('span'); amount.className = 'bar-number'; amount.textContent = number(value);
            track.appendChild(fill); row.append(label,track,amount); row.title = `${name}：¥${number(value)}`; chart.appendChild(row);
        });
    });
    document.querySelectorAll('[data-status-chart]').forEach(chart => {
        const counts = data.statusCount || {};
        const entries = [
            ['已获批', Number(counts.approved || 0), '#36ad8c'],
            ['审批中', ['submitted','chairman_passed','teacher_passed','leader_passed'].reduce((sum,status) => sum + Number(counts[status] || 0),0), '#5597ed'],
            ['已驳回', Number(counts.rejected || 0), '#e47b88']
        ];
        const total = entries.reduce((sum,[,value]) => sum + value,0);
        let previous = 0;
        const segments = entries.map(([,value,color]) => {const next = previous + (total ? value / total * 360 : 0); const segment = `${color} ${previous}deg ${next}deg`; previous = next; return segment;});
        const donut = chart.querySelector('.donut');
        donut.style.background = total ? `conic-gradient(${segments.join(',')})` : '#e5edf5';
        donut.setAttribute('aria-label', total ? entries.map(([label,value]) => `${label}${value}份`).join('，') : '暂无申请数据');
        const legend = chart.querySelector('.legend'); legend.replaceChildren();
        entries.forEach(([label,value,color]) => {
            const row = document.createElement('div'), name = document.createElement('span'), dot = document.createElement('i'), amount = document.createElement('strong'), unit = document.createElement('small');
            dot.className = 'tiny-dot'; dot.style.background = color; name.append(dot,document.createTextNode(label)); amount.textContent = value + ' '; unit.textContent = '份'; amount.appendChild(unit); row.append(name,amount); legend.appendChild(row);
        });
    });
})();
