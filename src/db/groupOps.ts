import { CuentasDB } from './db';
import type { TxGroup } from '../domain/types';

async function validarAgrupables(d: CuentasDB, txIds: string[]): Promise<void> {
  const txs = await d.transactions.bulkGet(txIds);
  for (const t of txs) {
    if (!t) throw new Error('Algún movimiento ya no existe');
    if (t.type !== 'income' && t.type !== 'expense') throw new Error('Solo se agrupan gastos e ingresos');
    if (t.groupId) throw new Error('Algún movimiento ya pertenece a un grupo');
  }
}

async function limpiarGroupId(d: CuentasDB, txIds: string[]): Promise<void> {
  await d.transactions.where('id').anyOf(txIds).modify((t) => { delete t.groupId; });
}

/** Crea un grupo transaccional con ≥2 gastos/ingresos sueltos. Todo-o-nada.
 *  v3: `categoryId` opcional = categoría del residuo virtual (ver categoriaDelResiduo). */
export async function agruparTxs(d: CuentasDB, txIds: string[], nota?: string, categoryId?: string): Promise<string> {
  if (txIds.length < 2) throw new Error('Selecciona al menos 2 movimientos');
  const grupo: TxGroup = {
    id: crypto.randomUUID(),
    nota: nota?.trim() || undefined,
    createdIso: new Date().toISOString(),
  };
  if (categoryId) grupo.categoryId = categoryId;
  await d.transaction('rw', d.transactions, d.txGroups, d.categories, async () => {
    if (categoryId && !(await d.categories.get(categoryId))) throw new Error('La categoría no existe');
    await validarAgrupables(d, txIds);
    await d.txGroups.add(grupo);
    await d.transactions.where('id').anyOf(txIds).modify((t) => { t.groupId = grupo.id; });
  });
  return grupo.id;
}

export async function anadirAGrupo(d: CuentasDB, groupId: string, txIds: string[]): Promise<void> {
  if (txIds.length === 0) throw new Error('Selecciona al menos 1 movimiento');
  await d.transaction('rw', d.transactions, d.txGroups, async () => {
    if (!(await d.txGroups.get(groupId))) throw new Error('El grupo no existe');
    await validarAgrupables(d, txIds);
    await d.transactions.where('id').anyOf(txIds).modify((t) => { t.groupId = groupId; });
  });
}

/** Saca movimientos de sus grupos; un grupo que baja de 2 miembros se disuelve. */
export async function quitarDeGrupo(d: CuentasDB, txIds: string[]): Promise<void> {
  await d.transaction('rw', d.transactions, d.txGroups, async () => {
    const txs = (await d.transactions.bulkGet(txIds)).filter((t) => t !== undefined);
    const gruposTocados = [...new Set(txs.map((t) => t.groupId).filter((g): g is string => !!g))];
    await limpiarGroupId(d, txIds);
    for (const gid of gruposTocados) {
      const restantes = await d.transactions.where('groupId').equals(gid).primaryKeys();
      if (restantes.length < 2) {
        await limpiarGroupId(d, restantes);
        await d.txGroups.delete(gid);
      }
    }
  });
}

/** Revierte el grupo completo: todos los miembros vuelven a ser movimientos normales. */
export async function desagrupar(d: CuentasDB, groupId: string): Promise<void> {
  await d.transaction('rw', d.transactions, d.txGroups, async () => {
    const miembros = await d.transactions.where('groupId').equals(groupId).primaryKeys();
    await limpiarGroupId(d, miembros);
    await d.txGroups.delete(groupId);
  });
}

/** Edita nota y/o categoría del grupo. `null` BORRA la clave; `undefined` no la toca. */
export async function actualizarGrupo(
  d: CuentasDB, groupId: string, patch: { nota?: string | null; categoryId?: string | null },
): Promise<void> {
  await d.transaction('rw', d.txGroups, d.categories, async () => {
    const grupo = await d.txGroups.get(groupId);
    if (!grupo) throw new Error('El grupo no existe');
    if (patch.categoryId && !(await d.categories.get(patch.categoryId))) throw new Error('La categoría no existe');
    const actualizado: TxGroup = { ...grupo };
    if (patch.nota !== undefined) {
      const limpia = patch.nota?.trim() ?? '';
      if (limpia === '') delete actualizado.nota;
      else actualizado.nota = limpia;
    }
    if (patch.categoryId !== undefined) {
      if (patch.categoryId) actualizado.categoryId = patch.categoryId;
      else delete actualizado.categoryId;
    }
    await d.txGroups.put(actualizado);
  });
}
