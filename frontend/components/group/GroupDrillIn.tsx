"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { MatchView } from "@/lib/api/bracket";
import type { AcceptOutcome } from "@/lib/paul-feedback";
import { formatMatchDateTime } from "@/lib/format-datetime";
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
  timeZone,
}: {
  matches: MatchView[];
  groupId: string;
  saveBetAction: (matchId: number, t1: number, t2: number, gid: string) => Promise<void>;
  acceptPaulAction: (matchId: number, gid: string) => Promise<AcceptOutcome | void>;
  locked?: boolean;
  timeZone: string;
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
            kickoffLabel={formatMatchDateTime(m.kickoffAt, timeZone)}
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

