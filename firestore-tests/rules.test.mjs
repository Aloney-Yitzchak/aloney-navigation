// Security rules tests (T06, NFR-10, SDD 8.2). Run with:  npm test
// They run against the local Firestore emulator, never the real project.
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertSucceeds, assertFails,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-aloney-navigation',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8') },
  });
});

after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'rooms/36'), { name: 'ט2', owner: null, entranceNodeId: 14, planYear: 'tashpaz' });
    await setDoc(doc(db, 'meta/version'), { number: 1 });
  });
});

const anonymous = () => env.unauthenticatedContext().firestore();
const student = () => env.authenticatedContext('student-uid', { email: 'student@a-y.org.il' }).firestore();
const staff = () => env.authenticatedContext('staff-uid', { email: 'teacher@a-y.org.il', staff: true }).firestore();

test('anyone can read the campus data, even without signing in', async () => {
  await assertSucceeds(getDoc(doc(anonymous(), 'rooms/36')));
  await assertSucceeds(getDoc(doc(anonymous(), 'meta/version')));
});

test('anonymous users cannot write', async () => {
  await assertFails(updateDoc(doc(anonymous(), 'rooms/36'), { owner: 'מישהו' }));
  await assertFails(setDoc(doc(anonymous(), 'nodes/1'), { lat: 32.49, lng: 34.98 }));
});

test('a signed-in user without the staff claim cannot write', async () => {
  await assertFails(updateDoc(doc(student(), 'rooms/36'), { owner: 'מישהו' }));
  await assertFails(deleteDoc(doc(student(), 'rooms/36')));
});

test('staff can update a room', async () => {
  await assertSucceeds(updateDoc(doc(staff(), 'rooms/36'), { owner: "רכזת שכבה ט'" }));
});

test('staff can bump the data version', async () => {
  await assertSucceeds(setDoc(doc(staff(), 'meta/version'), { number: 2 }));
});

test('a staff claim that is not exactly true does not count', async () => {
  const fake = env.authenticatedContext('x', { staff: 'true' }).firestore();
  await assertFails(updateDoc(doc(fake, 'rooms/36'), { owner: 'x' }));
});

// T20a: the list of buildings to photograph follows the same rule
test('staff can add a building to photograph and count photos; others cannot', async () => {
  await assertSucceeds(setDoc(doc(staff(), 'photoTasks/building_01'), { name: 'בניין המדעים', target: 40, order: 1 }));
  await assertSucceeds(updateDoc(doc(staff(), 'photoTasks/building_01'), { taken: 1 }));
  await assertFails(setDoc(doc(student(), 'photoTasks/building_02'), { name: 'x', target: 40, order: 2 }));
  await assertFails(updateDoc(doc(anonymous(), 'photoTasks/building_01'), { taken: 99 }));
});
