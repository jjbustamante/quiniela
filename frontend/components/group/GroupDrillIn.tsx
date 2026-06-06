"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import type { MatchView } from "@/lib/api/bracket";
import { formatMatchDateTime } from "@/lib/format-datetime";
import { MatchRow } from "./MatchRow";
import { NumpadScoreInput } from "./NumpadScoreInput";
import { PaulReasoningPanel } from "./PaulReasoningPanel";
import {
  singleMatchFeedback,
  pickKey,
  KEPT_HEADER_KEYS,
  type AcceptOutcome,
  type SingleFeedback,
} from "@/lib/paul-feedback";

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
  acceptPaulAction: (matchId: number, gid: string) => Promise<AcceptOutcome>;
  locked?: boolean;
  timeZone: string;
}) {
  const tGroup = useTranslations("group");
  const [editing, setEditing] = useState<{ matchId: number } | null>(null);
  const [, startTransition] = useTransition();
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<Map<number, { fb: SingleFeedback; keptKey: string }>>(
    new Map(),
  );

  const dismiss = (matchId: number) =>
    setFeedback((prev) => {
      const next = new Map(prev);
      next.delete(matchId);
      return next;
    });

  function renderFeedback(matchId: number) {
    const entry = feedback.get(matchId);
    if (!entry) return null;
    const { fb, keptKey } = entry;
    if (fb.kind === "locked")
      return (
        <PaulReasoningPanel
          header={tGroup("paulLocked")}
          dismissLabel={tGroup("paulDismiss")}
          onDismiss={() => dismiss(matchId)}
          tone="error"
        />
      );
    if (fb.kind === "error")
      return (
        <PaulReasoningPanel
          header={tGroup("paulError")}
          dismissLabel={tGroup("paulDismiss")}
          onDismiss={() => dismiss(matchId)}
          tone="error"
        />
      );
    const header =
      fb.kind === "kept"
        ? tGroup(keptKey)
        : tGroup("paulSaid", { t1: fb.scoreT1, t2: fb.scoreT2 });
    return (
      <PaulReasoningPanel
        header={header}
        reasoning={fb.reasoning}
        dismissLabel={tGroup("paulDismiss")}
        onDismiss={() => dismiss(matchId)}
      />
    );
  }

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
            paulPending={pendingId === m.id}
            feedback={renderFeedback(m.id)}
            onTapScore={() => {
              if (locked) return;
              setEditing({ matchId: m.id });
            }}
            onAskPaul={() => {
              if (locked || pendingId === m.id) return;
              const prior = { t1: m.betScoreT1, t2: m.betScoreT2 };
              setPendingId(m.id);
              startTransition(async () => {
                try {
                  const outcome = await acceptPaulAction(m.id, groupId);
                  const fb = singleMatchFeedback(prior, outcome);
                  const keptKey = pickKey(KEPT_HEADER_KEYS, Math.random());
                  setFeedback((prev) => new Map(prev).set(m.id, { fb, keptKey }));
                } finally {
                  setPendingId(null);
                }
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
