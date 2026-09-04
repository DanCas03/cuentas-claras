import { CuentasDB, seedIfEmpty } from './db';
import { agruparTxs, anadirAGrupo, quitarDeGrupo, desagrupar, actualizarGrupo } from './groupOps';
import { buildManualTx } from '../domain/builders';

async function dbConTxs() {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  const ids: string[] = [];
  for (const [type, amountMinor] of [['income', 500], ['expense', 350], ['expense', 120]] as const) {
    const tx = buildManualTx({ type, accountId: 'acc-efectivo', amountMinor, categoryId: 'cat-otros-gastos', dateIso: '2026-07-10' });
    await d.transactions.add(tx);
    ids.push(tx.id);
  }
  return { d, ids };
}

test('agruparTxs asigna groupId a todos y crea el grupo con nota', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1]], '  leche  ');
  expect((await d.txGroups.get(gid))?.nota).toBe('leche');
  expect((await d.transactions.get(ids[0]))?.groupId).toBe(gid);
  expect((await d.transactions.get(ids[1]))?.groupId).toBe(gid);
  expect((await d.transactions.get(ids[2]))?.groupId).toBeUndefined();
});

test('agruparTxs rechaza <2, tipos no agrupables y re-agrupar (todo-o-nada)', async () => {
  const { d, ids } = await dbConTxs();
  await expect(agruparTxs(d, [ids[0]])).rejects.toThrow('al menos 2');
  const transfer = { id: 'tr1', type: 'transfer' as const, accountId: 'acc-efectivo', toAccountId: 'acc-zinli', amountMinor: 100, toAmountMinor: 100, date: '2026-07-10', source: 'manual' as const };
  await d.transactions.add(transfer);
  await expect(agruparTxs(d, [ids[0], 'tr1'])).rejects.toThrow('gastos e ingresos');
  expect((await d.transactions.get(ids[0]))?.groupId).toBeUndefined(); // nada quedó a medias
  const gid = await agruparTxs(d, [ids[0], ids[1]]);
  await expect(agruparTxs(d, [ids[1], ids[2]])).rejects.toThrow('ya pertenece');
  expect((await d.txGroups.toArray()).map((g) => g.id)).toEqual([gid]); // no se creó un grupo fantasma
});

test('anadirAGrupo suma un miembro; a grupo inexistente lanza', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1]]);
  await anadirAGrupo(d, gid, [ids[2]]);
  expect(await d.transactions.where('groupId').equals(gid).count()).toBe(3);
  await expect(anadirAGrupo(d, 'no-existe', [ids[2]])).rejects.toThrow('no existe');
});

test('quitarDeGrupo con quórum mantiene el grupo; bajando de 2 lo disuelve', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1], ids[2]]);
  await quitarDeGrupo(d, [ids[2]]);
  expect(await d.transactions.where('groupId').equals(gid).count()).toBe(2);
  expect(await d.txGroups.get(gid)).toBeTruthy();
  await quitarDeGrupo(d, [ids[1]]);
  expect(await d.txGroups.get(gid)).toBeUndefined();          // disuelto
  expect((await d.transactions.get(ids[0]))?.groupId).toBeUndefined(); // el último quedó libre
});

test('desagrupar limpia groupId de todos y borra el grupo', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1]]);
  await desagrupar(d, gid);
  expect(await d.txGroups.count()).toBe(0);
  for (const id of [ids[0], ids[1]]) {
    expect((await d.transactions.get(id))?.groupId).toBeUndefined();
  }
});

test('agruparTxs acepta categoría; una categoría inexistente lanza sin crear nada', async () => {
  const { d, ids } = await dbConTxs();
  await expect(agruparTxs(d, [ids[0], ids[1]], 'leche', 'cat-no-existe')).rejects.toThrow('La categoría no existe');
  expect(await d.txGroups.count()).toBe(0);
  expect((await d.transactions.get(ids[0]))?.groupId).toBeUndefined();
  const gid = await agruparTxs(d, [ids[0], ids[1]], 'leche', 'cat-supermercado');
  expect(await d.txGroups.get(gid)).toMatchObject({ nota: 'leche', categoryId: 'cat-supermercado' });
});

test('actualizarGrupo: null borra la clave, undefined no la toca, categoría inexistente lanza', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1]], 'leche');
  await actualizarGrupo(d, gid, { categoryId: 'cat-supermercado' });
  expect((await d.txGroups.get(gid))?.categoryId).toBe('cat-supermercado');
  expect((await d.txGroups.get(gid))?.nota).toBe('leche'); // undefined no tocó la nota
  await actualizarGrupo(d, gid, { nota: '  pan  ' });
  expect(await d.txGroups.get(gid)).toMatchObject({ nota: 'pan', categoryId: 'cat-supermercado' });
  await expect(actualizarGrupo(d, gid, { categoryId: 'cat-no-existe' })).rejects.toThrow('La categoría no existe');
  expect((await d.txGroups.get(gid))?.categoryId).toBe('cat-supermercado'); // nada a medias
  await actualizarGrupo(d, gid, { categoryId: null });
  const g = await d.txGroups.get(gid);
  expect(g && 'categoryId' in g).toBe(false);
  await actualizarGrupo(d, gid, { nota: null });
  expect((await d.txGroups.get(gid)) && 'nota' in (await d.txGroups.get(gid))!).toBe(false);
  await expect(actualizarGrupo(d, 'no-existe', { nota: 'x' })).rejects.toThrow('El grupo no existe');
});
