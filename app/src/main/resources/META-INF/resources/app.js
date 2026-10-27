const $ = (id) => document.getElementById(id);
let selected = "alba",
  owners = [],
  detail = null,
  developer = null,
  drawerMode = null,
  busy = false,
  submittingOwner = null,
  selectedMatchId = null,
  pollBusy = false,
  lastChat = "",
  lastDecision = "",
  executionStatuses = new Map();
const decisionDrafts = new Map();
const freshFindRequest =
  "Find an existing open competitive doubles match for me now. Please start the search and negotiate within my saved preferences. I will separately approve participation.";
const node = (tag, text, className) => {
  const el = document.createElement(tag);
  if (text !== undefined) el.textContent = text;
  if (className) el.className = className;
  return el;
};
const button = (text, action, className) => {
  const el = node("button", text, className);
  el.type = "button";
  el.addEventListener("click", action);
  return el;
};
async function api(path, body, method = body ? "POST" : "GET") {
  const r = await fetch("/api" + path, {
    method,
    headers: { "Content-Type": "application/json" },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const data = await r.json();
  if (!r.ok)
    throw Error(data.message || data.error || `Request failed (${r.status}).`);
  return data;
}
function error(e) {
  $("error").textContent = e.message || String(e);
  $("error").hidden = false;
}
function clearError() {
  $("error").hidden = true;
}
function initials(name) {
  return name
    .split(" ")
    .map((w) => w[0])
    .join("");
}
function ownerName(id) {
  return owners.find((o) => o.id === id)?.name.split(" ")[0] || id;
}
function switchOwner(id) {
  selected = id;
  detail = null;
  $("working")?.remove();
  $("request-failure")?.remove();
  lastChat = "";
  closeDrawer();
  clearError();
  refresh();
}
function renderOwners() {
  const nav = $("owners");
  nav.replaceChildren();
  for (const o of owners) {
    const b = button(
      "",
      () => switchOwner(o.id),
      "owner" + (o.id === selected ? " selected" : ""),
    );
    b.setAttribute("aria-pressed", String(o.id === selected));
    const avatar = node("span", initials(o.name), "avatar");
    avatar.style.background = o.color;
    const t = node("span", undefined, "text");
    t.append(
      node("strong", o.name),
      node(
        "small",
        o.id === selected
          ? "Private conversation"
          : "Tap to see their conversation",
      ),
    );
    b.append(avatar, t, node("span", "›", "owner-arrow"));
    nav.append(b);
  }
}
function renderChat() {
  if (!detail) return;
  const o = owners.find((o) => o.id === selected);
  $("avatar").textContent = initials(detail.name);
  $("avatar").style.background = o?.color;
  $("owner-name").textContent = detail.name + "’s agent";
  $("agent-status").textContent = detail.pending.length
    ? "Waiting for your decision"
    : "Your private padel agent";
  const key = selected + JSON.stringify(detail.messages);
  if (key !== lastChat) {
    lastChat = key;
    const list = $("messages");
    list.replaceChildren();
    for (const m of detail.messages) {
      const b = node("div", undefined, "bubble " + m.role);
      if (m.role === "agent")
        b.append(node("span", "PADELPOT AGENT", "agent-label"));
      b.append(
        document.createTextNode(m.text),
        node(
          "small",
          new Date(m.time).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          }),
        ),
      );
      list.append(b);
    }
    list.scrollTop = list.scrollHeight;
  }
  renderWorking();
  renderDecision();
  renderSuggestions();
  if (drawerMode === "persona") renderPersona();
}
function renderWorking() {
  const status = detail?.latestRequest?.status;
  const working =
    submittingOwner === selected ||
    ["RUNNING", "QUEUED", "PENDING", "WAIT_RETRY"].includes(status);
  $("agent-status").textContent = working
    ? "Your agent is working…"
    : detail.pending.length
      ? "Waiting for your decision"
      : ["FAILED", "DLQ"].includes(status)
        ? "Your latest request failed"
        : "Your private padel agent";
  if (working && !$("working")) {
    const bubble = node("div", undefined, "bubble typing-bubble");
    bubble.id = "working";
    bubble.setAttribute("role", "status");
    bubble.setAttribute("aria-label", "Your agent is working");
    const dots = node("div", undefined, "typing-dots");
    dots.setAttribute("aria-hidden", "true");
    for (let i = 0; i < 3; i++) dots.append(node("span"));
    bubble.append(node("span", "PADELPOT AGENT", "agent-label"), dots);
    $("messages").append(bubble);
    $("messages").scrollTop = $("messages").scrollHeight;
  } else if (!working) $("working")?.remove();
  if (["FAILED", "DLQ"].includes(status) && !working) {
    if (!$("request-failure")) {
      const expired = detail?.latestRequest?.errorCode === "AWAIT_TIMEOUT";
      const failure = node(
        "div",
        expired
          ? "That approval expired after 15 minutes. It cannot be resumed. Send a new request to try again."
          : "Your latest request failed. A new message starts a fresh request; open Agent activity for details.",
        "bubble request-failure",
      );
      failure.id = "request-failure";
      failure.setAttribute("role", "status");
      if (selected !== "alba")
        failure.append(button("Start a new match search", () => send(freshFindRequest)));
      $("messages").append(failure);
    }
  } else $("request-failure")?.remove();
}
function fitComposer() {
  const input = $("message");
  input.style.height = "auto";
  input.style.height = Math.min(input.scrollHeight, 200) + "px";
}
function renderDecision() {
  const p = detail.pending[0];
  const key = JSON.stringify([
    selected,
    p?.id,
    p?.kind,
    p?.prompt,
    p?.field,
    p?.value,
    busy,
  ]);
  if (key === lastDecision) return;
  lastDecision = key;
  const container = $("decision");
  container.replaceChildren();
  container.hidden = !p;
  $("message").placeholder =
    !p || p.kind === "conversation"
      ? "Tell your agent what you have in mind…"
      : "Ask a question or propose different match terms…";
  if (!p) return;
  if (p.kind === "conversation") {
    container.append(
      node("p", "Reply to your agent’s question in the chat below."),
      button("Cancel this request", () => reply(p, "reject", "")),
    );
    container.querySelector("button").disabled = busy;
    return;
  }
  container.append(
    node(
      "h3",
      p.kind === "participation" || p.kind === "open"
        ? "YOUR APPROVAL IS REQUIRED"
        : "A QUICK CHECK WITH YOU",
    ),
    node("p", p.prompt),
  );
  const input = node("input");
  if (p.kind === "clarify") {
    input.placeholder = "Answer this permission question";
    input.maxLength = 1200;
    input.setAttribute("aria-label", "Answer this permission question");
    const draftKey = selected + ":" + p.id;
    input.value = decisionDrafts.get(draftKey) || "";
    input.addEventListener("input", () =>
      decisionDrafts.set(draftKey, input.value),
    );
    container.append(input);
  } else {
    container.append(node("p",
      "Questions and proposed changes go in the chat below. They leave this approval open."));
  }
  const actions = node("div", undefined, "actions");
  actions.append(
    button(
      p.kind === "declared" || p.kind === "inference"
        ? "Save this preference"
        : p.kind === "clarify"
          ? "Allow for this request"
          : p.kind === "disclosure"
            ? "Allow this field"
            : "Approve participation",
      () => reply(p, "approve", ""),
      "primary",
    ),
    button("Decline", () => reply(p, "reject", "")),
  );
  if (p.kind === "clarify")
    actions.append(
      button("Send clarification", () =>
        reply(p, "clarify", input.value.trim()),
      ),
    );
  container.append(actions);
  for (const b of actions.children) b.disabled = busy;
}
function renderSuggestions() {
  const el = $("suggestions");
  el.replaceChildren();
  if (detail.pending.length) return;
  const chips =
    selected === "alba"
      ? [
          [
            "Open Thursday at 19:00",
            "Open a competitive doubles match this Thursday at 19:00 in Barcelona. I prefer left.",
          ],
        ]
      : [
          [
            "Find me a match",
            freshFindRequest,
          ],
        ];
  chips.push([
    "Tell me what you know",
    "What do you still need to know about my padel preferences?",
  ]);
  if (detail.messages.some((m) => m.text.includes("match was simulated")))
    chips.unshift([
      "Give match feedback",
      "Post-match feedback: I liked the friendly pace. I think social matches suit me better. Please propose an inferred style preference.",
    ]);
  for (const [label, text] of chips)
    el.append(
      button(label, () => {
        $("message").value = text;
        fitComposer();
        $("message").focus();
      }),
    );
}
async function send(text) {
  if (busy) return;
  const question = detail?.pending[0];
  if (question?.kind === "conversation") {
    if (await reply(question, null, text)) {
      $("message").value = "";
      fitComposer();
    }
    return;
  }
  busy = true;
  submittingOwner = selected;
  renderWorking();
  $("send").disabled = true;
  clearError();
  try {
    const accepted = await api("/messages", {
      ownerId: selected,
      messageId: crypto.randomUUID(),
      text,
      ...(question ? { interactionId: question.id } : {}),
    });
    $("message").value = "";
    fitComposer();
    $("agent-status").textContent = "Your agent is thinking…";
    await refresh();
    watchExecution(accepted.executionId, selected);
  } catch (e) {
    error(e);
  } finally {
    busy = false;
    submittingOwner = null;
    if (detail) renderWorking();
    $("send").disabled = false;
  }
}
async function reply(p, choice, text) {
  if (busy) return;
  busy = true;
  submittingOwner = selected;
  renderWorking();
  clearError();
  renderDecision();
  try {
    await api("/messages", {
      ownerId: selected,
      messageId: crypto.randomUUID(),
      text,
      interactionId: p.id,
      choice,
    });
    decisionDrafts.delete(selected + ":" + p.id);
    watchExecution(p.executionId, selected);
    await refresh();
    return true;
  } catch (e) {
    error(e);
    return false;
  } finally {
    busy = false;
    submittingOwner = null;
    if (detail) renderWorking();
    renderDecision();
  }
}
async function watchExecution(id, owner) {
  for (let i = 0; i < 180; i++) {
    await new Promise((r) => setTimeout(r, 1000));
    try {
      const s = await api("/executions/" + id);
      if (
        selected === owner &&
        ["RUNNING", "QUEUED", "PENDING"].includes(s.status)
      )
        $("agent-status").textContent = "Your agent is negotiating…";
      if (["FAILED", "DLQ"].includes(s.status)) {
        if (selected === owner)
          error(
            Error(
              "Inference or execution failed. Open Agent activity for details.",
            ),
          );
        return;
      }
      if (
        ["SUCCEEDED", "WAITING_EXTERNAL", "CANCELLED", "EXPIRED"].includes(
          s.status,
        )
      ) {
        await refresh();
        return;
      }
    } catch {
      return;
    }
  }
}
function matchLabel(id) {
  return "#" + id.split(":").at(-1).slice(0, 8);
}
function renderMatch(matches) {
  const el = $("match");
  el.replaceChildren();
  const m = matches.find((m) => m.id === selectedMatchId) || matches.at(-1);
  if (matches.length > 1) {
    const selector = node("select", undefined, "match-selector");
    selector.setAttribute("aria-label", "Match to view");
    for (const match of matches) {
      const option = node(
        "option",
        `${matchLabel(match.id)} · ${match.hour}:00 · ${Object.keys(match.players).length} of 4 · ${match.status.toLowerCase()}`,
      );
      option.value = match.id;
      option.selected = match.id === m.id;
      selector.append(option);
    }
    selector.addEventListener("change", () => {
      selectedMatchId = selector.value;
      renderMatch(matches);
    });
    el.append(selector);
  }
  const card = node("div", undefined, "match-card");
  const label = node("div", undefined, "eyebrow");
  label.append(
    node("span", m ? "MATCH " + matchLabel(m.id) : "THE NEXT MATCH"),
    node("span", m?.status === "CONFIRMED" ? "✓" : "↗"),
  );
  card.append(label);
  if (!m) {
    card.append(
      node("h3", "A court. Four people."),
      node(
        "p",
        "Start with Alba’s agent to open a match. Then let the other agents explore it.",
      ),
      node("span", "Every place needs a human yes.", "status"),
    );
  } else if (m.rosterValid === false) {
    card.append(
      node("h3", "Saved roster needs a fresh match"),
      node(
        "p",
        "These recorded places cannot form two valid teams. Their sides have been preserved.",
      ),
      node(
        "p",
        Object.entries(m.players)
          .map(([id, side]) => `${ownerName(id)} · ${side}`)
          .join("\n"),
      ),
    );
  } else {
    card.append(
      node("h3", `${m.hour}:00 · ${m.style}`),
      node(
        "p",
        `${m.date} · ${m.area}\n${m.level || "Intermediate"} · No court booking included`,
      ),
    );
    const people = node("div", undefined, "match-teams");
    for (const team of ["A", "B"]) {
      const roster = node("div", undefined, "team-card");
      roster.append(node("strong", "Team " + team));
      for (const side of ["left", "right"]) {
        const id = Object.keys(m.players).find(
          (id) => m.teams?.[id] === team && m.players[id] === side,
        );
        const place = node("div", undefined, "court-place");
        place.append(
          node("small", side === "left" ? "Left" : "Right"),
          node("span", id ? ownerName(id) : "Open place"),
        );
        roster.append(place);
      }
      people.append(roster);
    }
    card.append(
      people,
      node(
        "span",
        m.played
          ? "Match simulated · feedback time"
          : m.cancelled
            ? "Cancelled"
            : `${Object.keys(m.players).length} of 4 places · ${m.status.toLowerCase()}`,
        "status",
      ),
    );
    if (m.status === "CONFIRMED" && !m.played)
      card.append(
        button(
          selected === m.organizer
            ? "Simulate match → feedback"
            : `Switch to ${ownerName(m.organizer)} to simulate`,
          async () => {
            if (selected !== m.organizer) {
              switchOwner(m.organizer);
              return;
            }
            try {
              await api(`/matches/${m.id}/simulate`, { ownerId: m.organizer });
              refresh();
            } catch (e) {
              error(e);
            }
          },
        ),
      );
  }
  el.append(card);
}
function openDrawer(mode) {
  drawerMode = mode;
  $("drawer").hidden = false;
  $("persona-button").setAttribute("aria-expanded", String(mode === "persona"));
  $("drawer-title").textContent =
    mode === "persona" ? "Your private preferences" : "Behind the conversation";
  mode === "persona" ? renderPersona() : renderDeveloper();
}
function closeDrawer() {
  drawerMode = null;
  $("drawer").hidden = true;
  $("persona-button").setAttribute("aria-expanded", "false");
}
function renderPersona() {
  if (!detail) return;
  const el = $("drawer-content");
  el.replaceChildren(
    node(
      "p",
      `${detail.name} · persona version ${detail.version}. This view belongs to the selected fictional owner. Other agents receive only permitted match terms.`,
    ),
  );
  for (const [field, f] of Object.entries(detail.persona)) {
    const row = node("div", undefined, "fact");
    const label = node("div");
    label.append(
      node(
        "strong",
        field === "side"
          ? "Court side: " +
              ({
                either: "Both sides OK",
                left: "Prefer left · either side OK",
                right: "Prefer right · either side OK",
                strong_left: "Left only",
                strong_right: "Right only",
              }[f.value] || f.value)
          : `${field}: ${f.value}`,
      ),
      node("small", `${f.source} · ${f.disclosure}`),
    );
    row.append(
      label,
      button("Delete", async () => {
        try {
          await api(`/owners/${selected}/persona/${field}`, null, "DELETE");
          await refresh();
        } catch (e) {
          error(e);
        }
      }),
    );
    el.append(row);
  }
  el.append(
    node(
      "p",
      "To correct a preference, tell your agent, for example “My level is intermediate”. You’ll review the extracted change before it is saved.",
    ),
    node("h3", "Private blocking rules"),
  );
  for (const o of owners.filter((o) => o.id !== selected))
    el.append(
      button(
        `${detail.blocked.includes(o.id) ? "Unblock" : "Block"} ${o.name.split(" ")[0]}`,
        async () => {
          try {
            await api(`/owners/${selected}/block`, {
              otherId: o.id,
              blocked: !detail.blocked.includes(o.id),
            });
            refresh();
          } catch (e) {
            error(e);
          }
        },
      ),
    );
  const live = detail.intents.filter(
    (i) => !i.cancelled && i.deadline > Date.now(),
  );
  if (live.length) {
    el.append(node("h3", "Current requests"));
    for (const i of live)
      el.append(
        button("Cancel " + i.requestId.slice(0, 8), async () => {
          try {
            await api(`/intents/${i.requestId}/cancel`, { ownerId: selected });
            refresh();
          } catch (e) {
            error(e);
          }
        }),
      );
  }
  el.append(
    node("h3", "Try the onboarding interview"),
    node(
      "p",
      "Start fresh with empty preferences for all four fictional owners. Complete or reject pending decisions first.",
    ),
    button("Start fresh interviews", async () => {
      try {
        await api("/demo/reset", { onboarding: true });
        lastChat = "";
        await refresh();
      } catch (e) {
        error(e);
      }
    }),
  );
}
function renderDeveloper() {
  if (!developer) return;
  const el = $("drawer-content");
  el.replaceChildren(
    node("p", developer.provider),
    node("p", developer.topology),
    node(
      "p",
      "Policy events and agent messages below contain permitted structured terms. Native TPF replay files are retained in output/tpf-replay. Agents propose; deterministic code decides whether an action is allowed.",
    ),
  );
  for (const e of developer.events.toReversed()) {
    const row = node("div", undefined, "event");
    row.append(node("strong", e.type.replaceAll("_", " ")));
    const d = e.data;
    if (e.type === "AGENT_MESSAGE")
      row.append(
        node(
          "p",
          `${ownerName(d.from)} → ${ownerName(d.to)}: ${d.action} · ${d.field || `${d.hour}:00 · ${d.style} · ${d.side}`}`,
        ),
      );
    else
      row.append(
        node(
          "code",
          Object.entries(d)
            .map(([k, v]) => `${k}: ${v}`)
            .join("\n"),
        ),
      );
    if (e.type === "ROOT_STARTED")
      row.append(
        button("Inspect TPF execution", async () => {
          try {
            const status = await api("/executions/" + d.executionId);
            executionStatuses.set(d.executionId, status);
            row.append(node("code", JSON.stringify(status, null, 2)));
          } catch (err) {
            row.append(node("p", err.message));
          }
        }),
      );
    if (executionStatuses.has(d.executionId))
      row.append(
        node(
          "code",
          JSON.stringify(executionStatuses.get(d.executionId), null, 2),
        ),
      );
    el.append(row);
  }
}
async function refresh() {
  if (pollBusy) return;
  pollBusy = true;
  const current = selected;
  try {
    const [view, d, dev] = await Promise.all([
      api("/view"),
      api("/owners/" + current),
      api("/developer"),
    ]);
    owners = view.owners;
    developer = dev;
    renderOwners();
    renderMatch(view.matches);
    $("provider").textContent = dev.provider;
    if (current === selected) {
      detail = d;
      renderChat();
    }
    if (drawerMode === "developer") renderDeveloper();
  } catch (e) {
    error(e);
  } finally {
    pollBusy = false;
  }
}
$("composer").addEventListener("submit", (e) => {
  e.preventDefault();
  const text = $("message").value.trim();
  if (text) send(text);
});
$("message").addEventListener("input", fitComposer);
fitComposer();
$("message").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    $("composer").requestSubmit();
  }
});
$("persona-button").addEventListener("click", () =>
  drawerMode === "persona" ? closeDrawer() : openDrawer("persona"),
);
$("developer-button").addEventListener("click", () => openDrawer("developer"));
$("drawer-close").addEventListener("click", closeDrawer);
$("reset").addEventListener("click", async () => {
  try {
    await api("/demo/reset", { onboarding: false });
    lastChat = "";
    await refresh();
  } catch (e) {
    error(e);
  }
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") closeDrawer();
});
await refresh();
setInterval(refresh, 1500);
