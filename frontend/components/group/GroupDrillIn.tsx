"use client";

import { useState, useTransition } from "react";
import type { MatchView } from "@/lib/api/bracket";
import { MatchRow } from "./MatchRow";
import { NumpadScoreInput } from "./NumpadScoreInput";

export function GroupDrillIn({
  matches,
  groupId,
  saveBetAction,
  acceptPaulAction,
}: {
  matches: MatchView[];
  groupId: string;
  saveBetAction: (matchId: number, t1: number, t2: number, gid: string) => Promise<void>;
  acceptPaulAction: (matchId: number, gid: string) => Promise<void>;
}) {
  const [editing, setEditing] = useState<{ matchId: number } | null>(null);
  const [, startTransition] = useTransition();

  return (
    <>
      <div className="space-y-2">
        {matches.map((m) => (
          <MatchRow
            key={m.id}
            match={m}
            onTapScore={() => setEditing({ matchId: m.id })}
            onAskPaul={() =>
              startTransition(() => {
                acceptPaulAction(m.id, groupId);
              })
            }
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
