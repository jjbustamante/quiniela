import { api } from "./client";

export type SubgroupMember = {
  userId: number;
  displayName: string;
  paid: boolean;
  amountCents: number | null;
};
export type SubgroupView = {
  expectedCents: number;
  collectedCents: number;
  ownSettled: boolean;
  members: SubgroupMember[];
};

export async function getMySubgroup(): Promise<SubgroupView> {
  return api<SubgroupView>("/api/payments/my-subgroup");
}

export async function markPaid(
  userId: number,
  paid: boolean,
  amountCents?: number,
  note?: string,
): Promise<void> {
  await api(`/api/payments/${userId}/paid`, {
    method: "PUT",
    body: JSON.stringify({ paid, amountCents: amountCents ?? null, note: note ?? null }),
  });
}

export type LedgerMember = {
  userId: number;
  displayName: string;
  role: string;
  paid: boolean;
  amountCents: number | null;
  note: string | null;
};
export type CaptainGroup = {
  captainId: number;
  captainName: string;
  captainPaid: boolean;
  captainSettled: boolean;
  expectedCents: number;
  collectedCents: number;
  members: LedgerMember[];
};
export type LedgerView = {
  potCents: number;
  paidCount: number;
  memberCount: number;
  captains: CaptainGroup[];
  orphans: LedgerMember[];
};

export async function getAdminLedger(): Promise<LedgerView> {
  return api<LedgerView>("/api/admin/payments");
}

export async function markSettled(captainId: number, settled: boolean): Promise<void> {
  await api(`/api/admin/payments/${captainId}/settled`, {
    method: "PUT",
    body: JSON.stringify({ settled }),
  });
}
