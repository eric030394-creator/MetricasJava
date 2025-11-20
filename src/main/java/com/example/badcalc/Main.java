package com.example.badcalc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;             // Uso de la interfaz List en vez de la implementación concreta
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Main {

    // ----------------- REGISTRADOR -----------------
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    // Leo BADCALC_LOG_LEVEL para ajustar el nivel de logs en tiempo de ejecución.
    // Valores válidos: FINE, INFO, WARNING, SEVERE.
    static {
        String level = System.getenv().getOrDefault("BADCALC_LOG_LEVEL", "INFO").toUpperCase();
        try {
            logger.setLevel(Level.parse(level));
        } catch (IllegalArgumentException ex) {
            // Valor inválido -> uso INFO y registro el problema.
            logger.setLevel(Level.INFO);
            logger.log(Level.WARNING, "BADCALC_LOG_LEVEL inválido: {0}. Usando INFO", level);
        }
    }

    // ----------------- CAMPOS (VISIBILIDAD, FINALIDAD, NOMBRADO) -----------------
    private static final List<String> history = new ArrayList<>();
    // Historial de operaciones (expuesto como copia inmutable desde getHistory()).

    private static String lastEntry = ""; // Última entrada añadida al historial.

    private static int counter = 0; // Contador de operaciones realizadas.

    private static final Random random = new Random(); // Fuente de aleatoriedad compartida.

    private static final String API_KEY = System.getenv().getOrDefault("BADCALC_API_KEY", "NOT_SECRET_KEY");
    // La clave se toma de la variable de entorno; no dejar secretos en el código.

    // ----------------- MÉTODOS AUXILIARES -----------------

    private static void writeHistoryLine(String line) {
        // Apendo la línea a history.txt; registro cualquier IOException que ocurra.
        try (FileWriter fw = new FileWriter("history.txt", true)) {
            fw.write(line + System.lineSeparator());
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "No se pudo escribir history.txt: {0}", ioe.getMessage());
        }
    }

    private static void addToHistory(String line) {
        if (line == null) {
            // Evitar añadir valores nulos al historial.
            return;
        }
        history.add(line);
        lastEntry = line;
        writeHistoryLine(line);
    }

    public static List<String> getHistory() {
        return List.copyOf(history);
    }

    public static String getLastEntry() { return lastEntry; }

    public static int getCounter() { return counter; }

    public static double parse(String s) {
        if (s == null) return 0;
        try {
            s = s.replace(',', '.').trim();
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            // Registro el fallo de parseo para depuración en nivel FINE.
            logger.log(Level.FINE, "parse: valor inválido ''{0}'' -> retornando 0", s);
            return 0;
        }
    }

    public static double badSqrt(double v) {
        double g = v;
        int k = 0;
        while (Math.abs(g * g - v) > 0.0001 && k < 100000) {
            g = (g + v / g) / 2.0;
            k++;
            if (k % 5000 == 0) {
                // Ceder CPU periódicamente durante iteraciones prolongadas.
                Thread.yield();
            }
        }
        return g;
    }

    public static double compute(String a, String b, String op) {
        double left = parse(a);            // Nombres locales claros y descriptivos.
        double right;
        right = parse(b);

        try {
            switch (op) {
                case "+":
                    return left + right;
                case "-":
                    return left - right;
                case "*":
                    return left * right;
                case "/":
                    if (right == 0.0) {
                        logger.log(Level.WARNING, "compute: división por cero detectada. left={0}", left);
                        return left >= 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                    }
                    return left / right;
                case "^":
                    return Math.pow(left, right);
                case "%":
                    return left % right;
                default:
                    logger.log(Level.FINE, "compute: operación desconocida ''{0}''", op);
                    return 0;
            }
        } catch (Exception e) {
            // Registro de excepción inesperada para diagnóstico.
            logger.log(Level.SEVERE, "compute: excepción inesperada", e);
            return 0;
        }
    }

    private static String buildPrompt(String system, String userTemplate, String userInput) {
        return system + "\n\nTEMPLATE_START\n" + userTemplate + "\nTEMPLATE_END\nUSER:" + userInput;
    }

    private static String sendToLLM(String prompt) {
        // Evitar loguear el prompt completo por seguridad; registrar sólo un hash para depuración.
        logger.fine("LLM prompt hash: " + Integer.toHexString(prompt.hashCode()));
        logger.fine("LLM prompt length: " + prompt.length());
        return "SIMULATED_LLM_RESPONSE";
    }

    private static boolean sleepRandomOrStop() {
        try {
            Thread.sleep(random.nextInt(2)); // Dormir 0-1 ms; intención: breve pausa entre operaciones.
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void createAutoPromptFile() {
        // Para evitar crear archivos con instrucciones peligrosas en producción,
        // la creación se ejecuta sólo si ENABLE_AUTO_PROMPT=1.
        if (!"1".equals(System.getenv("ENABLE_AUTO_PROMPT"))) {
            return;
        }

        try (FileWriter fw = new FileWriter(new File("AUTO_PROMPT.txt"))) {
            fw.write("=== AUTO PROMPT (TEST) ===\nThis file is for local testing only.\n");
        } catch (IOException e) {
            logger.log(Level.WARNING, "No se pudo crear AUTO_PROMPT.txt: {0}", e.getMessage());
        }
    }

    private static void runInteractionLoop(Scanner sc) {
        boolean running = true;

        while (running) {
            logger.info("BAD CALC (Java very bad edition)");
            logger.info("1:+ 2:- 3:* 4:/ 5:^ 6:% 7:LLM 8:hist 0:exit");
            logger.info("opt: ");

            String opt = sc.nextLine();

            if ("0".equals(opt)) {
                running = false;
                continue;
            }

            String a = "0";
            String b = "0";
            if (!"7".equals(opt) && !"8".equals(opt)) {
                logger.info("a: ");
                a = sc.nextLine();
                logger.info("b: ");
                b = sc.nextLine();
            }

            if ("7".equals(opt)) {
                handleLLMInteraction(sc);
            } else if ("8".equals(opt)) {
                printHistory();
            } else {
                String op = switch (opt) {
                    case "1" -> "+";
                    case "2" -> "-";
                    case "3" -> "*";
                    case "4" -> "/";
                    case "5" -> "^";
                    case "6" -> "%";
                    default -> "";
                };

                double res = compute(a, b, op);
                String line = a + "|" + b + "|" + op + "|" + res;
                addToHistory(line);
                logger.log(Level.INFO, "= {0}", res);
                counter++;

                if (!sleepRandomOrStop()) {
                    running = false;
                }
            }
        }
    }

    private static void createLeftoverFileIfNeeded() {
        try (FileWriter fw = new FileWriter("leftover.tmp")) {
            // Archivo intencionalmente vacío: se crea para cumplir requisitos de prueba.
        } catch (IOException e) {
            logger.log(Level.WARNING, "No se pudo crear leftover.tmp: {0}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        // main orquesta los pasos de alto nivel; la configuración del logger se realiza en el bloque static.
        createAutoPromptFile();

        try (Scanner sc = new Scanner(System.in)) {
            runInteractionLoop(sc);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error general en main", e);
        }

        createLeftoverFileIfNeeded();
    }

    private static void handleLLMInteraction(Scanner sc) {
        logger.info("Enter user template (will be concatenated UNSAFELY):");
        String tpl = sc.nextLine();
        logger.info("Enter user input:");
        String uin = sc.nextLine();
        String sys = "System: You are an assistant.";
        String prompt = buildPrompt(sys, tpl, uin);
        String resp = sendToLLM(prompt);
        logger.log(Level.INFO, "LLM RESP: {0}", resp);
    }

    private static void printHistory() {
        for (String h : getHistory()) {
            logger.info(h);
        }
    }
}
