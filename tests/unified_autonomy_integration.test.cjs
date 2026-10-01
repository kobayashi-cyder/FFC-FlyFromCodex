const fs = require('fs');
const assert = require('assert');

const read = p => fs.readFileSync(p, 'utf8');
const index = read('app/src/main/assets/index.html');
const caps = read('app/src/main/assets/capability-tools.js');
const threads = read('app/src/main/assets/thread-router.js');
const proxy = read('app/src/main/assets/proxy-agent.js');
const runtime = read('app/src/main/assets/webview-runtime.js');

const requiredIndex = [
  'latestConversationText',
  'threadContextFromGoal',
  'researchConversationAnswer',
  "AGENT_SKILL_KEY='banc888_fly_agent_skills_v1'",
  'agentObserveSkill',
  'agentObserveFeedbackSkill',
  'agentAutonomyStatus',
  'autonomy.status',
  'skillsStatus:agentAutonomyStatus',
  'agentSkillBias(tool)'
];
for (const s of requiredIndex) assert(index.includes(s), 'index missing '+s);

const requiredReview = [
  "REVIEW_KEY='FFC_REVIEW_EPISODES_V4'",
  'GENERATE→TEST→EVALUATE→DIAGNOSE→REPAIR→RETEST→LEARN',
  'reviewSelect',
  'diagnostic-repair',
  'capability.review.status',
  'IQ.evolve',
  'generationTrace'
];
for (const s of requiredReview) assert(caps.includes(s), 'capability-tools missing '+s);

const requiredLogs = [
  "LOG_FORMAT='BANC888-conversation-log-v1'",
  'BANC888_CONVERSATION_LOGS',
  'exportNDJSON',
  'copyJSON',
  'quietAgentRun(x.q.text)',
  '{goal:text,context:context(t)}',
  "runtime:'v7.4-unified-autonomy'"
];
for (const s of requiredLogs) assert(threads.includes(s), 'thread-router missing '+s);

assert(proxy.includes("version:'2.1-unified-autonomy'"), 'proxy version not integrated');
assert(proxy.includes('review gate rejected all candidates'), 'proxy review gate missing');
assert(runtime.includes("const BUILD='7.4-unified-autonomy';"), 'runtime build marker missing');
assert(runtime.includes('conversationLogs:!!window.BANC888_CONVERSATION_LOGS'), 'runtime log status missing');
assert(runtime.includes('skillsStatus'), 'runtime autonomy status missing');
assert(runtime.includes('reviewStatus'), 'runtime review status missing');

console.log('unified-autonomy-integration: PASS');
