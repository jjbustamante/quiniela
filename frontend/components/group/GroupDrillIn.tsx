"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { MatchView } from "@/lib/api/bracket";
import { MatchRow } from "./MatchRow";
import { NumpadScoreInput } from "./NumpadScoreInput";

/**
 * GroupDrillIn — wraps the MatchRow list and shows the NumpadScoreInput
 * sheet over the top when a row is tapped. Unchanged contract from before;
 * only the visual atoms (MatchRow, Numpad) were restyled.
 */
export function GroupDrillIn({
  matches,
  groupId,
  saveBetAction,
  acceptPaulAction,
  locked = false,
}: {
  matches: MatchView[];
  groupId: string;
  saveBetAction: (matchId: number, t1: number, t2: number, gid: string) => Promise<void>;
  acceptPaulAction: (matchId: number, gid: string) => Promise<void>;
  locked?: boolean;
}) {
  const tGroup = useTranslations("group");
  const [editing, setEditing] = useState<{ matchId: number } | null>(null);
  const [, startTransition] = useTransition();

  return (
    <>
      <div className="flex flex-col gap-2">
        {matches.map((m) => (
          <MatchRow
            key={m.id}
            match={m}
            kickoffLabel={formatKickoff(m.kickoffAt)}
            paulLabelEmpty={tGroup("paulDecide")}
            paulLabelFilled={tGroup("paulChange")}
            locked={locked}
            onTapScore={() => {
              if (locked) return;
              setEditing({ matchId: m.id });
            }}
            onAskPaul={() => {
              if (locked) return;
              startTransition(() => {
                acceptPaulAction(m.id, groupId);
              });
            }}
          />
        ))}
      </div>
      {editing &&
        (() => {
          const editingMatch = matches.find((m) => m.id === editing.matchId);
          return (
            <NumpadScoreInput
              side="both"
              match={
                editingMatch
                  ? {
                      team1Name: editingMatch.team1Name,
                      team1Flag: editingMatch.team1Flag,
                      team2Name: editingMatch.team2Name,
                      team2Flag: editingMatch.team2Flag,
                    }
                  : undefined
              }
              onConfirm={(s) =>
                startTransition(() => {
                  saveBetAction(editing.matchId, s.t1, s.t2, groupId);
                  setEditing(null);
                })
              }
              onCancel={() => setEditing(null)}
            />
          );
        })()}
    </>
  );
}

function formatKickoff(iso: string): string {
  try {
    const d = new Date(iso);
    const date = d.toLocaleDateString("es-CO", { day: "2-digit", month: "short" }).toUpperCase().replace(".", "");
    const time = d.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", hour12: false });
    return `${date} · ${time}`;
  } catch {
    return iso;
  }
}
