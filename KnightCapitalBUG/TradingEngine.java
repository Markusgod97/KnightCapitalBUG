package com.knightcapital.buggy;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * Motor principal de trading - Versión con errores
 *
 * ISSUES DETECTADOS POR SONARQUBE:
 *  [BLOCKER]  Línea 38  - Sin circuit breaker ni límite de pérdidas
 *  [BLOCKER]  Línea 52  - Bucle infinito sin condición de salida por error
 *  [CRITICAL] Línea 61  - NullPointerException potencial no controlada
 *  [MAJOR]    Línea 71  - Resultado de operación crítica ignorado
 */
public class TradingEngine implements Runnable {

    private static final Logger log = Logger.getLogger(TradingEngine.class.getName());

    private final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private volatile boolean marketOpen = true;
    private final OrderRouter router = new OrderRouter();

    // =========================================================
    // BUG #1 [BLOCKER] - SonarQube: java:S2189 / Bucle infinito
    // No hay: contador de pérdidas, umbral de detención,
    // ni mecanismo de emergencia. El sistema ejecuta órdenes
    // indefinidamente aunque algo esté fallando.
    //
    // Analogía Knight Capital: 4,000,000 órdenes en 45 minutos
    // sin que el sistema se detuviera automáticamente.
    // =========================================================
    @Override
    public void run() {
        log.info("Motor de trading iniciado");

        while (marketOpen) {  // ← solo se detiene cuando el mercado cierra
            try {
                Order order = orderQueue.poll();

                if (order != null) {
                    // =========================================
                    // BUG #2 [CRITICAL] - SonarQube: java:S2259
                    // NullPointerException si getPosition()
                    // devuelve null (símbolo no inicializado)
                    // =========================================
                    Position pos = PositionTracker.getPosition(order.getSymbol());
                    pos.updateQuantity(order.getQuantity()); // ← NPE si pos == null

                    router.routeOrder(order);

                    // =========================================
                    // BUG #3 [MAJOR] - SonarQube: java:S2201
                    // El valor retornado por confirmOrder()
                    // indica éxito/fallo pero es ignorado.
                    // =========================================
                    ExchangeGateway.confirmOrder(order);  // ← return value ignored
                }

            } catch (Exception e) {
                // ← SonarQube: catching generic Exception oculta errores específicos
                log.warning("Error en ciclo de trading: " + e.getMessage());
                // No hay: incremento de contador de errores,
                // activación de circuit breaker, ni alerta a operadores
            }
        }
    }

    public void stopTrading() {
        this.marketOpen = false;
        // ← Solo funciona si el operador llama manualmente este método.
        //   En el incidente de Knight Capital, los operadores
        //   tardaron 45 minutos en encontrar cómo detener el sistema.
    }
}