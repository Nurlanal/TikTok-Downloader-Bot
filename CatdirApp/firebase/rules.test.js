// Firestore qaydalarının testləri (Firebase emulyatorunda işləyir): npm test
const { test, before, after, beforeEach } = require('node:test');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, collection, query, where, getDocs } = require('firebase/firestore');

let env;
const now = Date.now();
const db = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();

function orderDoc(over = {}) {
  return {
    customerId: 'alice', customerName: 'Alice', customerPhone: '+994501112233',
    storeId: 's1', storeName: 'Market', storePhone: '', storeAddress: '',
    items: [{ productId: 'p1', name: 'Süd', priceQ: 210, qty: 2 }],
    subtotalQ: 420, deliveryFeeQ: 150, totalQ: 570,
    address: 'Atatürk pr. 25, mənzil 14', note: '', payment: 'cash', status: 'NEW',
    courierId: null, courierName: '', courierPhone: '', cancelReason: '',
    createdAt: now, updatedAt: now, timeline: { NEW: now },
    ...over,
  };
}
const step = (status, extra = {}) => ({ status, updatedAt: Date.now(), [`timeline.${status}`]: Date.now(), ...extra });
const take = (uid) => ({ courierId: uid, courierName: uid, courierPhone: '1', updatedAt: Date.now() });

async function seedOrder(id, over = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), 'orders', id), orderDoc(over)));
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'catdir-test',
    firestore: { rules: fs.readFileSync(__dirname + '/firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
after(async () => env.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    const u = (id, role) => setDoc(doc(d, 'users', id), { name: id, phone: '1', email: id + '@x.az', role, address: '', online: false, createdAt: now });
    await Promise.all([u('alice', 'CUSTOMER'), u('bob', 'CUSTOMER'), u('cora', 'COURIER'), u('dan', 'COURIER'), u('adam', 'ADMIN')]);
    await setDoc(doc(d, 'stores', 's1'), { name: 'Market', category: 'Market', deliveryFeeQ: 150, minOrderQ: 0, etaMinutes: 25, isOpen: true });
    await setDoc(doc(d, 'stores', 's2'), { name: 'Bağlı', category: 'Market', deliveryFeeQ: 150, minOrderQ: 0, etaMinutes: 25, isOpen: false });
    await setDoc(doc(d, 'stores', 's1', 'products', 'p1'), { name: 'Süd', priceQ: 210, available: true, category: '', description: '' });
  });
});

// ---------------- İstifadəçilər ----------------

test('qeydiyyat: yalnız MÜŞTƏRİ rolu ilə öz sənədini yarada bilər', async () => {
  const base = { name: 'Nigar', phone: '1', email: 'n@x.az', address: '', online: false, createdAt: now };
  await assertSucceeds(setDoc(doc(db('nigar'), 'users', 'nigar'), { ...base, role: 'CUSTOMER' }));
  await assertFails(setDoc(doc(db('eve'), 'users', 'eve'), { ...base, role: 'ADMIN' }));
  await assertFails(setDoc(doc(db('eve'), 'users', 'eve2'), { ...base, role: 'CUSTOMER' }));
  await assertFails(setDoc(doc(db(null), 'users', 'x'), { ...base, role: 'CUSTOMER' }));
});

test('istifadəçi öz rolunu dəyişə bilməz, admin başqasının rolunu dəyişə bilər', async () => {
  await assertSucceeds(updateDoc(doc(db('alice'), 'users', 'alice'), { name: 'Alisa', address: 'Yeni ünvan' }));
  await assertFails(updateDoc(doc(db('alice'), 'users', 'alice'), { role: 'ADMIN' }));
  await assertFails(updateDoc(doc(db('alice'), 'users', 'alice'), { online: true })); // müştəri "online" ola bilməz
  await assertSucceeds(updateDoc(doc(db('cora'), 'users', 'cora'), { online: true }));
  await assertFails(updateDoc(doc(db('cora'), 'users', 'bob'), { role: 'COURIER' }));
  await assertSucceeds(updateDoc(doc(db('adam'), 'users', 'bob'), { role: 'COURIER' }));
  await assertFails(updateDoc(doc(db('adam'), 'users', 'bob'), { role: 'SUPERMAN' }));
  await assertFails(updateDoc(doc(db('adam'), 'users', 'adam'), { role: 'CUSTOMER' }));
});

test('başqasının profilini yalnız admin oxuya bilər', async () => {
  await assertSucceeds(getDoc(doc(db('alice'), 'users', 'alice')));
  await assertFails(getDoc(doc(db('alice'), 'users', 'bob')));
  await assertFails(getDoc(doc(db('cora'), 'users', 'alice')));
  await assertSucceeds(getDoc(doc(db('adam'), 'users', 'alice')));
});

// ---------------- Mağazalar ----------------

test('mağaza və məhsulları hamı oxuyur, yalnız admin yazır', async () => {
  await assertSucceeds(getDoc(doc(db('alice'), 'stores', 's1')));
  await assertSucceeds(getDocs(collection(db('cora'), 'stores', 's1', 'products')));
  await assertFails(getDoc(doc(db(null), 'stores', 's1')));
  await assertFails(updateDoc(doc(db('alice'), 'stores', 's1'), { deliveryFeeQ: 0 }));
  await assertFails(setDoc(doc(db('cora'), 'stores', 's1', 'products', 'p2'), { name: 'x', priceQ: 1 }));
  await assertSucceeds(setDoc(doc(db('adam'), 'stores', 's1', 'products', 'p2'), { name: 'Çay', priceQ: 640, available: true }));
});

// ---------------- Sifariş yaratmaq ----------------

test('müştəri düzgün sifariş yarada bilər', async () => {
  await assertSucceeds(setDoc(doc(db('alice'), 'orders', 'o1'), orderDoc()));
});

test('səhv sifarişlər rədd edilir', async () => {
  const d = db('alice');
  await assertFails(setDoc(doc(d, 'orders', 'a'), orderDoc({ customerId: 'bob' })));          // başqasının adına
  await assertFails(setDoc(doc(d, 'orders', 'b'), orderDoc({ status: 'ACCEPTED' })));          // status saxtalaşdırma
  await assertFails(setDoc(doc(d, 'orders', 'c'), orderDoc({ courierId: 'cora' })));           // kuryer təyini
  await assertFails(setDoc(doc(d, 'orders', 'd'), orderDoc({ deliveryFeeQ: 0, totalQ: 420 }))); // çatdırılma haqqı saxta
  await assertFails(setDoc(doc(d, 'orders', 'e'), orderDoc({ totalQ: 100 })));                 // cəm uyğun deyil
  await assertFails(setDoc(doc(d, 'orders', 'f'), orderDoc({ storeId: 's2' })));               // bağlı mağaza
  await assertFails(setDoc(doc(d, 'orders', 'g'), orderDoc({ items: [] })));                   // boş
  await assertFails(setDoc(doc(d, 'orders', 'h'), orderDoc({ address: 'x' })));                // ünvan yoxdur
  await assertFails(setDoc(doc(d, 'orders', 'i'), orderDoc({ payment: 'card' })));
  await assertFails(setDoc(doc(db(null), 'orders', 'j'), orderDoc()));
});

// ---------------- Oxumaq ----------------

test('sifarişi kim görə bilər', async () => {
  await seedOrder('o1');
  await assertSucceeds(getDoc(doc(db('alice'), 'orders', 'o1')));
  await assertSucceeds(getDoc(doc(db('adam'), 'orders', 'o1')));
  await assertFails(getDoc(doc(db('bob'), 'orders', 'o1')));
  await assertFails(getDoc(doc(db('cora'), 'orders', 'o1'))); // NEW — kuryer hələ görməməlidir
  await seedOrder('o2', { status: 'ACCEPTED', timeline: { NEW: now, ACCEPTED: now } });
  await assertSucceeds(getDoc(doc(db('cora'), 'orders', 'o2')));
  await seedOrder('o3', { status: 'READY', courierId: 'dan', timeline: { NEW: now, ACCEPTED: now, READY: now } });
  await assertFails(getDoc(doc(db('cora'), 'orders', 'o3'))); // başqa kuryerin sifarişi
  await assertSucceeds(getDoc(doc(db('dan'), 'orders', 'o3')));
});

test('proqramdakı sorğular icazə alır', async () => {
  await seedOrder('o1');
  await seedOrder('o2', { status: 'READY', timeline: { NEW: now, ACCEPTED: now, READY: now } });
  const mine = await assertSucceeds(getDocs(query(collection(db('alice'), 'orders'), where('customerId', '==', 'alice'))));
  if (mine.size !== 2) throw new Error('müştəri 2 sifariş görməlidir, gördü: ' + mine.size);
  const avail = await assertSucceeds(getDocs(query(collection(db('cora'), 'orders'),
    where('courierId', '==', null), where('status', 'in', ['ACCEPTED', 'READY']))));
  if (avail.size !== 1) throw new Error('kuryer 1 sifariş görməlidir, gördü: ' + avail.size);
  await assertSucceeds(getDocs(query(collection(db('cora'), 'orders'), where('courierId', '==', 'cora'))));
  await assertFails(getDocs(collection(db('cora'), 'orders')));      // filtrsiz — olmaz
  await assertFails(getDocs(collection(db('alice'), 'orders')));
  await assertSucceeds(getDocs(collection(db('adam'), 'orders')));
});

// ---------------- Həyat dövrü ----------------

test('tam axın: qəbul → hazır → kuryer götürür → yolda → çatdırıldı', async () => {
  await seedOrder('o1');
  const ref = (uid) => doc(db(uid), 'orders', 'o1');
  await assertFails(updateDoc(ref('alice'), step('ACCEPTED')));        // müştəri qəbul edə bilməz
  await assertFails(updateDoc(ref('cora'), take('cora')));             // NEW — hələ götürmək olmaz
  await assertSucceeds(updateDoc(ref('adam'), step('ACCEPTED')));
  await assertFails(updateDoc(ref('alice'), step('CANCELLED')));       // qəbuldan sonra müştəri ləğv edə bilməz
  await assertFails(updateDoc(ref('cora'), take('dan')));              // başqasının adına götürmək
  await assertSucceeds(updateDoc(ref('cora'), take('cora')));
  await assertFails(updateDoc(ref('dan'), take('dan')));               // artıq götürülüb
  await assertFails(updateDoc(ref('cora'), step('PICKED_UP')));        // hələ hazır deyil
  await assertSucceeds(updateDoc(ref('adam'), step('READY')));
  await assertFails(updateDoc(ref('dan'), step('PICKED_UP')));         // onun sifarişi deyil
  await assertFails(updateDoc(ref('cora'), step('PICKED_UP', { totalQ: 1 }))); // məbləği dəyişmək olmaz
  await assertSucceeds(updateDoc(ref('cora'), step('PICKED_UP')));
  await assertFails(updateDoc(ref('adam'), step('CANCELLED')));        // yolda olanı admin ləğv edə bilməz
  await assertFails(updateDoc(ref('cora'), { status: 'DELIVERED', updatedAt: 1, 'timeline.PICKED_UP': 1 })); // vaxtı saxtalaşdırma
  await assertSucceeds(updateDoc(ref('cora'), step('DELIVERED')));
  await assertFails(updateDoc(ref('cora'), step('PICKED_UP')));        // geri qaytarmaq olmaz
});

test('müştəri yeni sifarişi ləğv edə bilər, başqası yox', async () => {
  await seedOrder('o1');
  await assertFails(updateDoc(doc(db('bob'), 'orders', 'o1'), step('CANCELLED', { cancelReason: 'x' })));
  await assertSucceeds(updateDoc(doc(db('alice'), 'orders', 'o1'), step('CANCELLED', { cancelReason: 'Fikrimi dəyişdim' })));
});

test('heç kim sifarişi silə bilməz', async () => {
  await seedOrder('o1');
  const { deleteDoc } = require('firebase/firestore');
  await assertFails(deleteDoc(doc(db('adam'), 'orders', 'o1')));
  await assertFails(deleteDoc(doc(db('alice'), 'orders', 'o1')));
});
