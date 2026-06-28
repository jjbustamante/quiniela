package io.quiniela.api.paul;

import org.springframework.stereotype.Component;

/** Pure builder for Paul's prompts. No I/O, no LLM — fully unit-testable. */
@Component
public class MatchContextBuilder {

  static final String SYSTEM_PROMPT =
      """
      Eres "Pulpo Paul", un pulpo oráculo que predice resultados del Mundial 2026.
      Respondes SOLO con el esquema estructurado pedido. El campo "reasoning" va en
      español, con tono divertido y de oráculo, en 1 o 2 frases. Predice un marcador
      realista (la mayoría de partidos terminan 0-0 .. 3-1). "confidence" entre 0 y 1.
      """;

  public String systemPrompt() {
    return SYSTEM_PROMPT;
  }

  public String userPrompt(
      String roundCode,
      String groupCode,
      String team1Name,
      String team1Code,
      Integer team1Ranking,
      String team2Name,
      String team2Code,
      Integer team2Ranking) {
    boolean knockout = !"GROUP".equals(roundCode);
    StringBuilder sb = new StringBuilder();
    sb.append("Fase: ").append(stageLabel(roundCode)).append('\n');
    if (!knockout && groupCode != null) sb.append("Grupo ").append(groupCode).append('\n');
    sb.append("Local: ").append(team1Name).append(" (").append(team1Code).append(")");
    if (team1Ranking != null) sb.append(" — ranking FIFA: ").append(team1Ranking);
    sb.append('\n');
    sb.append("Visitante: ").append(team2Name).append(" (").append(team2Code).append(")");
    if (team2Ranking != null) sb.append(" — ranking FIFA: ").append(team2Ranking);
    sb.append('\n');
    sb.append("Predice el marcador (goles del local y del visitante).");
    if (knockout) {
      sb.append(
          "\nEs eliminación directa: alguien debe avanzar. Si predices empate en tiempo"
              + " reglamentario, indica en \"advancing\" qué equipo avanza por penales:"
              + " \"LOCAL\" o \"VISITANTE\".");
    }
    return sb.toString();
  }

  private static String stageLabel(String roundCode) {
    return switch (roundCode) {
      case "GROUP" -> "fase de grupos";
      case "R32" -> "dieciseisavos de final";
      case "R16" -> "octavos de final";
      case "QF" -> "cuartos de final";
      case "SF" -> "semifinal";
      case "THIRD_PLACE" -> "partido por el tercer puesto";
      case "FINAL" -> "final";
      default -> roundCode;
    };
  }
}
