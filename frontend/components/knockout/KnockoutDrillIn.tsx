"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { MatchView } from "@/lib/api/bracket";
import { formatMatchDateTime } from "@/lib/format-datetime";
import { MatchRow } from "@/components/group/MatchRow";
import { NumpadScoreInput } from "@/components/group/NumpadScoreInput";

export function KnockoutDrillIn({
  matches,
  roundCode,
  saveBetAction,
  acceptPaulAction,
  locked = false,
  timeZone,
}: {
  matches: MatchView[];
  roundCode: string;
  saveBetAction: (matchId: number, t1: number, t2: number, roundCode: string, predictedWinnerId?: number | null) => Promise<void>;
  acceptPaulAction: (matchId: number, roundCode: string) => Promise<void>;
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
              if (locked || m.team1Code == null || m.team2Code == null) return;
              setEditing({ matchId: m.id });
            }}
            onAskPaul={() => {
              if (locked || m.team1Code == null || m.team2Code == null) return;
              startTransition(() => {
                acceptPaulAction(m.id, roundCode);
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
              knockout
              match={
                editingMatch
                  ? {
                      team1Id: editingMatch.team1Id,
                      team1Name: editingMatch.team1Name,
                      team1Flag: editingMatch.team1Flag,
                      team2Id: editingMatch.team2Id,
                      team2Name: editingMatch.team2Name,
                      team2Flag: editingMatch.team2Flag,
                    }
                  : undefined
              }
              onConfirm={(s) =>
                startTransition(() => {
                  saveBetAction(editing.matchId, s.t1, s.t2, roundCode, s.predictedWinnerId);
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
