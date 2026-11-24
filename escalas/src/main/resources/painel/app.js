(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const state = { schedules: [], avisosUrl: null, volunteerIndex: 0 };
  const make = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  };
  const date = value => new Date(value + 'T12:00:00').toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });
  function notice(message, error = false) {
    $('notice').textContent = message;
    $('notice').className = 'notice' + (error ? ' error' : '');
    $('notice').hidden = false;
  }
  async function request(url, options = {}) {
    const response = await fetch(url, { ...options, signal: AbortSignal.timeout(8000) });
    const data = await response.json();
    if (!response.ok) throw new Error(data.erro || 'Não foi possível concluir a operação.');
    return { data, response };
  }
  function showView() {
    const inbox = location.hash === '#avisos';
    $('schedules-view').hidden = inbox;
    $('inbox-view').hidden = !inbox;
    $('breadcrumb').textContent = inbox ? 'Caixa de avisos' : 'Escalas';
    $('page-title').textContent = inbox ? 'A equipe fica por dentro.' : 'Cada pessoa, no seu lugar.';
    $('page-description').textContent = inbox ? 'Acompanhe os avisos de cada voluntário, em um só lugar.' : 'Prepare as escalas e acompanhe os avisos da sua equipe.';
    document.querySelectorAll('[data-nav]').forEach(a => a.classList.toggle('active', a.dataset.nav === (inbox ? 'avisos' : 'escalas')));
  }
  function status(id, online, text) {
    $(id).textContent = '● ' + text;
    $(id).className = online ? 'healthy' : 'unhealthy';
  }
  async function health() {
    const [producer, consumer] = await Promise.allSettled([
      request('/health'),
      state.avisosUrl ? request(state.avisosUrl + '/health') : Promise.reject(new Error('Avisos não configurado'))
    ]);
    const producerUp = producer.status === 'fulfilled';
    status('producer-status', producerUp, producerUp ? 'Escalas disponível' : 'Escalas indisponível');
    const consumerUp = consumer.status === 'fulfilled';
    status('consumer-status', consumerUp, consumerUp ? 'Avisos disponível' : 'Avisos indisponível');
    if (consumerUp) {
      const data = consumer.value.data;
      $('metric-events').textContent = data.eventosProcessados;
      $('metric-duplicates').textContent = data.duplicadosIgnorados;
      status('queue-status', data.filaConectada, data.filaConectada ? 'Fila conectada' : 'Aguardando fila');
    } else {
      $('metric-events').textContent = '—';
      $('metric-duplicates').textContent = '—';
      status('queue-status', false, 'Fila sem confirmação');
    }
  }
  function button(text, action, id, style = 'secondary') {
    const b = make('button', 'button row-action ' + style, text);
    b.type = 'button'; b.dataset.action = action; b.dataset.id = id;
    return b;
  }
  function renderSchedules() {
    const filter = $('status-filter').value;
    const filtered = state.schedules.filter(s => filter === 'ALL' || s.status === filter);
    const rows = $('schedule-rows'); rows.replaceChildren();
    $('schedules-empty').hidden = filtered.length !== 0;
    $('schedules-empty').querySelector('h3').textContent = state.schedules.length ? 'Nenhuma escala neste status' : 'Uma nova escala começa aqui';
    $('schedules-empty').querySelector('p').textContent = state.schedules.length ? 'Troque o filtro ou cadastre uma nova escala.' : 'Cadastre um evento e escolha quem vai servir. Depois, publique para gerar os avisos da equipe.';
    for (const s of filtered) {
      const row = make('tr');
      const eventCell = make('td'); const event = make('div', 'event-cell');
      event.append(make('span', 'event-symbol', '▦'));
      const title = make('div'); title.append(make('span', 'event-name', s.evento), make('small', '', s.voluntarios.length + (s.voluntarios.length === 1 ? ' pessoa na equipe' : ' pessoas na equipe')));
      event.append(title); eventCell.append(event);
      const people = make('td', '', s.voluntarios.map(v => v.nome).join(', '));
      people.style.maxWidth = '200px'; people.style.whiteSpace = 'normal'; people.style.lineHeight = '1.7';
      const statusCell = make('td'); statusCell.append(make('span', 'badge ' + (s.status === 'PUBLICADA' ? 'published' : 'draft'), s.status === 'PUBLICADA' ? '● Publicada' : '● Rascunho'));
      const actionsCell = make('td'); const actions = make('div', 'row-actions');
      if (s.status === 'PUBLICADA') actions.append(button('Reentregar', 'replay', s.id), button('Ver avisos →', 'inbox', s.id, 'subtle'));
      else actions.append(button('Publicar escala', 'publish', s.id, 'primary'));
      actionsCell.append(actions);
      row.append(eventCell, make('td', '', date(s.data)), people, statusCell, actionsCell);
      rows.append(row);
    }
    $('metric-schedules').textContent = state.schedules.length;
    $('nav-count').textContent = state.schedules.length;
    const people = new Map();
    state.schedules.forEach(s => s.voluntarios.forEach(v => people.set(v.id, v.nome)));
    $('volunteer-options').replaceChildren();
    for (const [id, name] of people) {
      const option = make('option'); option.value = id; option.label = name;
      $('volunteer-options').append(option);
    }
  }
  async function loadSchedules() {
    state.schedules = (await request('/escalas')).data;
    renderSchedules();
  }
  function addVolunteer(id = '', name = '') {
    if ($('volunteer-fields').children.length >= 100) return;
    const index = ++state.volunteerIndex;
    const row = make('div', 'volunteer-row');
    for (const [field, label, value, max] of [['id', 'Identificador', id, 64], ['nome', 'Nome', name, 120]]) {
      const inputLabel = make('label', '', label);
      const input = make('input');
      input.id = `volunteer-${index}-${field}`; input.name = field; input.value = value;
      input.required = true; input.maxLength = max; input.autocomplete = 'off'; input.placeholder = field === 'id' ? 'Ex.: ana' : 'Ex.: Ana';
      inputLabel.htmlFor = input.id; inputLabel.append(input); row.append(inputLabel);
    }
    const remove = make('button', 'icon-button', '×'); remove.type = 'button'; remove.setAttribute('aria-label', 'Remover voluntário');
    remove.addEventListener('click', () => {
      if ($('volunteer-fields').children.length > 1) row.remove();
      else { row.querySelectorAll('input').forEach(i => { i.value = ''; }); row.querySelector('input').focus(); }
    });
    row.append(remove); $('volunteer-fields').append(row);
  }
  function openForm() {
    $('schedule-form').reset(); $('volunteer-fields').replaceChildren(); $('form-error').hidden = true;
    const now = new Date();
    $('event-date').value = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
    addVolunteer(); $('schedule-dialog').showModal(); $('event-name').focus();
  }
  async function queryInbox() {
    const id = $('volunteer-id').value.trim();
    if (!id || !state.avisosUrl) return;
    const submit = $('query-inbox'); submit.disabled = true; submit.textContent = 'Consultando…';
    try {
      const { data, response } = await request(state.avisosUrl + '/avisos?voluntarioId=' + encodeURIComponent(id));
      const cache = response.headers.get('X-Cache');
      $('cache-badge').textContent = cache === 'HIT' ? 'HIT · leitura do cache' : cache === 'MISS' ? 'MISS · leitura do banco' : 'Cache não informado';
      $('cache-badge').className = 'badge ' + (cache === 'HIT' ? 'cache-hit' : cache === 'MISS' ? 'cache-miss' : 'neutral');
      $('cache-help').textContent = `${data.length} ${data.length === 1 ? 'aviso' : 'avisos'} para ${id} · Consulta às ${new Date().toLocaleTimeString('pt-BR')}. Cache por 60 segundos, invalidado a cada novo aviso. Consulte novamente para comparar.`;
      const results = $('inbox-results'); results.replaceChildren();
      if (!data.length) {
        const empty = make('div', 'panel empty');
        empty.append(make('span', 'empty-icon', '☷'), make('h3', '', 'Nenhum aviso por enquanto'), make('p', '', 'Publique uma escala com esse identificador e consulte novamente após o processamento.'));
        results.append(empty);
      }
      for (const aviso of data) {
        const card = make('article', 'panel message'); card.append(make('span', 'message-symbol', '✓'));
        const body = make('div'); body.append(make('h3', '', 'Uma nova escala para ' + aviso.voluntarioNome), make('p', '', aviso.mensagem), make('small', '', new Date(aviso.criadoEm).toLocaleString('pt-BR') + ' · Evento ' + aviso.eventoId));
        card.append(body); results.append(card);
      }
    } catch (e) {
      $('cache-badge').textContent = 'Consulta indisponível'; $('cache-badge').className = 'badge neutral';
      $('inbox-results').replaceChildren(make('div', 'panel empty', 'Não foi possível atualizar os avisos. Tente novamente quando o serviço estiver disponível.'));
      notice('Não foi possível consultar os avisos: ' + e.message, true);
    } finally { submit.disabled = false; submit.textContent = 'Consultar avisos'; }
  }
  $('new-schedule').addEventListener('click', openForm);
  $('empty-new').addEventListener('click', openForm);
  $('close-dialog').addEventListener('click', () => $('schedule-dialog').close());
  $('cancel-dialog').addEventListener('click', () => $('schedule-dialog').close());
  $('add-volunteer').addEventListener('click', () => addVolunteer());
  $('status-filter').addEventListener('change', renderSchedules);
  $('inbox-form').addEventListener('submit', e => { e.preventDefault(); queryInbox(); });
  window.addEventListener('hashchange', showView);
  $('refresh').addEventListener('click', async () => {
    $('refresh').disabled = true;
    try { await Promise.all([loadSchedules(), health()]); } catch (e) { notice('Não foi possível atualizar: ' + e.message, true); }
    finally { $('refresh').disabled = false; }
  });
  $('schedule-form').addEventListener('submit', async e => {
    e.preventDefault(); if ($('save-schedule').disabled) return;
    const volunteers = [...$('volunteer-fields').children].map(row => ({ id: row.querySelector('[name=id]').value.trim(), nome: row.querySelector('[name=nome]').value.trim() }));
    if (new Set(volunteers.map(v => v.id)).size !== volunteers.length) { $('form-error').textContent = 'Cada voluntário deve ter um identificador diferente.'; $('form-error').hidden = false; return; }
    $('save-schedule').disabled = true; $('save-schedule').textContent = 'Salvando…';
    try {
      await request('/escalas', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ evento: $('event-name').value.trim(), data: $('event-date').value, voluntarios: volunteers }) });
      $('schedule-dialog').close(); location.hash = '#escalas'; $('status-filter').value = 'ALL';
      notice('Rascunho salvo. Revise a equipe na lista e clique em Publicar escala para gerar os avisos.');
      await loadSchedules();
    } catch (e) { $('form-error').textContent = e.message; $('form-error').hidden = false; }
    finally { $('save-schedule').disabled = false; $('save-schedule').textContent = 'Salvar rascunho'; }
  });
  $('schedule-rows').addEventListener('click', async e => {
    const action = e.target.closest('button[data-action]'); if (!action || action.disabled) return;
    const schedule = state.schedules.find(s => s.id === action.dataset.id); if (!schedule) return;
    if (action.dataset.action === 'inbox') {
      $('volunteer-id').value = schedule.voluntarios[0].id; location.hash = '#avisos'; showView(); await queryInbox(); return;
    }
    const publish = action.dataset.action === 'publish'; const original = action.textContent;
    action.disabled = true; action.textContent = publish ? 'Publicando…' : 'Reentregando…';
    try {
      await request(`/escalas/${encodeURIComponent(schedule.id)}/${publish ? 'publicar' : 'reentregar'}`, { method: 'POST' });
      notice(publish ? 'Escala publicada! Os avisos serão gerados pela fila. Use Ver avisos para consultar a equipe.' : 'Reentrega agendada com o mesmo evento. O número de duplicados ignorados aumenta após o consumo, sem criar novos avisos.');
      await loadSchedules(); await health();
    } catch (e) { notice(e.message, true); }
    finally { action.disabled = false; action.textContent = original; }
  });
  async function start() {
    showView();
    try { state.avisosUrl = (await request('/painel/config')).data.avisosUrl; await loadSchedules(); }
    catch (e) { notice('Não foi possível carregar o painel. ' + e.message, true); }
    await health();
    let polling = false;
    setInterval(async () => { if (polling || document.hidden) return; polling = true; try { await health(); } finally { polling = false; } }, 5000);
  }
  start();
})();
