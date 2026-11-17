// Seeds the local Auth and Firestore emulators with the fictional households the Play Store
// screenshots show, relative to today. It wipes the emulators first.
//
//   firebase emulators:start --only auth,firestore --project choretracker-dev-1faf5
//   node docs/play-store/screenshots/seed.mjs
//   ./gradlew :app:installDebug -Pchoretracker.firebase.useEmulators=true
//
// Sign in as anna@example.com (English) or anna.cs@example.com (Czech) with PASSWORD below,
// on a 1080x1920 emulator (`adb shell wm size 1080x1920`) in System UI demo mode at 9:41.
// Take the Czech set with the emulator's system language set to Czech: dates follow it, not the
// in-app language. Insights uses the Month period.

const PROJECT = process.env.FIREBASE_PROJECT ?? 'choretracker-dev-1faf5';
const AUTH = 'http://127.0.0.1:9099';
const FIRESTORE = 'http://127.0.0.1:8080';
const PASSWORD = 'screenshots';
const DOCUMENTS = `projects/${PROJECT}/databases/(default)/documents`;
const HISTORY_DAYS = 200;
const DAY = 24 * 60 * 60 * 1000;

const CHORES = [
  { key: 'bathroom', en: 'Cleaning the bathroom', cs: 'Úklid koupelny', category: 'CLEANING', every: 7, last: 9, upNext: 'david' },
  { key: 'dinner', en: 'Cooking dinner', cs: 'Vaření večeře', category: 'COOKING', every: 1, last: 1, upNext: 'anna' },
  { key: 'vacuum', en: 'Vacuuming', cs: 'Vysávání', category: 'CLEANING', every: 7, last: 6, upNext: 'anna' },
  { key: 'dishes', en: 'Dishes', cs: 'Nádobí', category: 'CLEANING', every: 1, last: 0, anna: 0.7 },
  { key: 'laundry', en: 'Laundry', cs: 'Praní', category: 'CLEANING', every: 4, last: 2, anna: 0.6 },
  { key: 'sheets', en: 'Change bed sheets', cs: 'Převléct postele', category: 'CLEANING', every: 14, last: 8, anna: 0.5, together: 0.4 },
  { key: 'groceries', en: 'Groceries', cs: 'Nákup', category: 'SHOPPING', every: 7, last: 5, anna: 0.45, together: 0.2 },
  { key: 'bins', en: 'Take out bins', cs: 'Vynést koše', category: 'OUTDOOR', every: 7, last: 3, upNext: 'david' },
  { key: 'plants', en: 'Water plants', cs: 'Zalít květiny', category: 'OUTDOOR', every: 3, last: 2, anna: 0.65 },
  { key: 'leaves', en: 'Rake leaves', cs: 'Hrabání listí', category: 'OUTDOOR', every: 14, last: 10, anna: 0.3, together: 0.5 },
  { key: 'recycling', en: 'Recycling', cs: 'Třídění odpadu', category: 'OTHER', every: 7, last: 4, upNext: 'anna' },
];

const HOUSEHOLDS = [
  {
    lang: 'en',
    name: 'Our Home',
    inviteCode: 'HOME42',
    joinCode: 'WELCOME',
    anna: 'anna@example.com',
    david: 'david@example.com',
    askedOfAnna: { chores: ['groceries'], note: 'Working late tonight, thanks!' },
    askedOfDavid: { chores: ['laundry'], note: null },
  },
  {
    lang: 'cs',
    name: 'Náš domov',
    inviteCode: 'DOMOV7',
    joinCode: 'VITEJTE',
    anna: 'anna.cs@example.com',
    david: 'david.cs@example.com',
    askedOfAnna: { chores: ['groceries'], note: 'Dnes budu v práci dlouho, díky!' },
    askedOfDavid: { chores: ['laundry'], note: null },
  },
];

let seed = 7;
const random = () => {
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};

const now = new Date();
const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());

function daysAgoAt(days, hour = 8 + Math.floor(random() * 12)) {
  const date = new Date(startOfToday.getTime() - days * DAY);
  date.setHours(hour, Math.floor(random() * 60));
  return date < now ? date : new Date(now.getTime() - 60 * 60 * 1000);
}

function encode(value) {
  if (value === null || value === undefined) return { nullValue: null };
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(encode) } };
  if (typeof value === 'boolean') return { booleanValue: value };
  if (typeof value === 'number') return { integerValue: String(value) };
  return { stringValue: value };
}

async function request(url, init = {}) {
  const response = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner', ...init.headers },
  });
  if (!response.ok) throw new Error(`${init.method ?? 'GET'} ${url}: ${response.status} ${await response.text()}`);
  return response.json();
}

async function createUser(localId, email, displayName) {
  await request(`${AUTH}/identitytoolkit.googleapis.com/v1/projects/${PROJECT}/accounts`, {
    method: 'POST',
    body: JSON.stringify({ localId, email, password: PASSWORD, displayName }),
  });
}

async function commit(documents) {
  for (let i = 0; i < documents.length; i += 400) {
    const writes = documents.slice(i, i + 400).map(([path, data]) => ({
      update: {
        name: `${DOCUMENTS}/${path}`,
        fields: Object.fromEntries(Object.entries(data).map(([key, value]) => [key, encode(value)])),
      },
    }));
    await request(`${FIRESTORE}/v1/${DOCUMENTS}:commit`, { method: 'POST', body: JSON.stringify({ writes }) });
  }
}

function completionDays(chore) {
  const days = [];
  for (let day = chore.last; day <= HISTORY_DAYS; ) {
    days.push(day);
    const jitter = chore.every >= 3 ? Math.round((random() - 0.5) * 2) : random() < 0.12 ? 1 : 0;
    day += Math.max(1, chore.every + jitter);
  }
  return days;
}

async function seedHousehold(household) {
  const householdId = `household-${household.lang}`;
  const uid = { anna: `anna-${household.lang}`, david: `david-${household.lang}` };
  const memberId = { anna: `member-anna-${household.lang}`, david: `member-david-${household.lang}` };
  const createdAt = daysAgoAt(HISTORY_DAYS + 5, 19);
  const rotationStartedAt = daysAgoAt(120, 7);
  const documents = [];
  const counts = { anna: 0, david: 0, together: 0 };
  const recentCounts = { anna: 0, david: 0, together: 0 };

  await createUser(uid.anna, household.anna, 'Anna');
  await createUser(uid.david, household.david, 'David');

  documents.push([`households/${householdId}`, {
    id: householdId,
    name: household.name,
    ownerUserId: uid.anna,
    inviteCode: household.inviteCode,
    createdAt,
  }]);
  for (const who of ['anna', 'david']) {
    documents.push([`households/${householdId}/members/${uid[who]}`, {
      id: memberId[who],
      householdId,
      userId: uid[who],
      displayName: who === 'anna' ? 'Anna' : 'David',
      role: who === 'anna' ? 'OWNER' : 'ADMIN',
      active: true,
      removedAt: null,
      email: household[who],
    }]);
    documents.push([`users/${uid[who]}`, {
      userId: uid[who],
      householdId,
      displayName: who === 'anna' ? 'Anna' : 'David',
      updatedAt: now,
    }]);
  }

  const invites = [
    { id: `invite-david-${household.lang}`, code: household.joinCode, createdAt, consumedAt: createdAt, consumedByMemberId: memberId.david },
    { id: `invite-open-${household.lang}`, code: household.inviteCode, createdAt: daysAgoAt(1, 20) },
  ];
  for (const invite of invites) {
    documents.push([`households/${householdId}/invites/${invite.id}`, { householdId, consumedAt: null, consumedByMemberId: null, ...invite }]);
    documents.push([`inviteCodes/${invite.code}`, { householdId, inviteId: invite.id }]);
  }

  for (const chore of CHORES) {
    const choreId = `chore-${chore.key}-${household.lang}`;
    const rotation = chore.upNext ? ['anna', 'david'] : [];
    documents.push([`households/${householdId}/chores/${choreId}`, {
      id: choreId,
      householdId,
      name: chore[household.lang],
      isActive: true,
      createdAt,
      deletedAt: null,
      frequencyDays: chore.every,
      category: chore.category,
      createdByUserId: uid.anna,
      rotationMemberIds: rotation.map((who) => memberId[who]),
      rotationStartedAt: chore.upNext ? rotationStartedAt : null,
    }]);

    // Newest first: the newest rotation completion goes to whoever isn't up next, so the count since
    // the rotation started stays even or puts them ahead.
    let rotationTurn = chore.upNext === 'anna' ? 'david' : 'anna';
    completionDays(chore).forEach((day, index) => {
      const at = day === 0 ? new Date(now.getTime() - (2 + random() * 2) * 60 * 60 * 1000) : daysAgoAt(day);
      let participants;
      if (chore.upNext && at > rotationStartedAt) {
        participants = [rotationTurn];
        rotationTurn = rotationTurn === 'anna' ? 'david' : 'anna';
      } else if (random() < (chore.together ?? 0)) {
        participants = ['anna', 'david'];
      } else {
        participants = [random() < (chore.anna ?? 0.55) ? 'anna' : 'david'];
      }
      const bucket = participants.length > 1 ? 'together' : participants[0];
      counts[bucket] += 1;
      if (day < 30) recentCounts[bucket] += 1;
      const completionId = `completion-${chore.key}-${household.lang}-${index}`;
      documents.push([`households/${householdId}/completions/${completionId}`, {
        id: completionId,
        householdId,
        choreId,
        createdAt: at,
        createdByUserId: uid[participants[0]],
        note: null,
        participantMemberIds: participants.map((who) => memberId[who]),
      }]);
    });
  }

  const requests = [
    { from: 'david', to: 'anna', sentAt: new Date(now.getTime() - 2 * 60 * 60 * 1000), ...household.askedOfAnna },
    { from: 'anna', to: 'david', sentAt: new Date(now.getTime() - 20 * 60 * 60 * 1000), ...household.askedOfDavid },
  ];
  requests.forEach((ask, batchIndex) => {
    const batchId = `batch-${household.lang}-${batchIndex}`;
    for (const key of ask.chores) {
      const requestId = `request-${key}-${household.lang}`;
      documents.push([`households/${householdId}/requests/${requestId}`, {
        id: requestId,
        householdId,
        batchId,
        choreId: `chore-${key}-${household.lang}`,
        fromMemberId: memberId[ask.from],
        toMemberId: memberId[ask.to],
        toUserId: uid[ask.to],
        createdByUserId: uid[ask.from],
        note: ask.note,
        createdAt: ask.sentAt,
        state: 'OPEN',
        resolvedAt: null,
      }]);
    }
  });

  await commit(documents);
  console.log(`${household.name}: ${documents.length} documents, all time`, counts, 'last 30 days', recentCounts);
}

await request(`${FIRESTORE}/emulator/v1/${DOCUMENTS}`, { method: 'DELETE' });
await request(`${AUTH}/emulator/v1/projects/${PROJECT}/accounts`, { method: 'DELETE' });
for (const household of HOUSEHOLDS) await seedHousehold(household);
