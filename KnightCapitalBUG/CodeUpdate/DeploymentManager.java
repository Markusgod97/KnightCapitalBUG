package com.knightcapital.fixed;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Gestor de despliegue - Versión CORREGIDA
 *
 * CORRECCIONES APLICADAS:
 *  [CRITICAL → FIXED]  Resultado de deploy() verificado y manejado
 *  [CRITICAL → FIXED]  Verificación de consistencia post-deploy en TODOS los servidores
 *  [MAJOR    → FIXED]  Rollback automático ante fallo parcial
 *
 * Esta implementación habría evitado el desastre del 01/08/2012:
 * el servidor #8 sin actualizar habría sido detectado, el deploy
 * habría sido revertido, y PowerPeg nunca se habría activado.
 */
public class DeploymentManager {

    private static final Logger log = Logger.getLogger(DeploymentManager.class.getName());

    private final AlertService alertService;

    public DeploymentManager(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * Despliega en todos los servidores con verificación completa.
     *
     * Flujo:
     *  1. Guardar versión actual para rollback
     *  2. Desplegar en cada servidor y verificar resultado
     *  3. Verificar que TODOS tienen la misma versión activa
     *  4. Si algo falla → rollback automático completo
     *
     * @throws DeploymentException si el deploy no es consistente en todos los servidores
     */
    public void deployToAllServers(String newVersion, List<Server> servers)
            throws DeploymentException {

        // Guardar versión actual para poder hacer rollback
        String previousVersion = servers.get(0).getRunningVersion();
        log.info("Deploy iniciado: " + previousVersion + " → " + newVersion);

        // =========================================================
        // CORRECCIÓN #1 - Verificar resultado de cada deploy
        // =========================================================
        for (Server server : servers) {
            log.info("Desplegando en servidor: " + server.getId());

            boolean deployOk = server.deploy(newVersion); // ← ahora se VERIFICA

            if (!deployOk) {
                log.severe("Fallo en servidor: " + server.getId() + ". Iniciando rollback.");
                alertService.page("Deploy fallido en " + server.getId() + ". Rollback activado.");

                // Rollback inmediato en TODOS los servidores
                rollbackAll(previousVersion, servers);
                throw new DeploymentException(
                    "Deploy abortado: fallo en servidor " + server.getId() +
                    ". Todos los servidores han vuelto a versión " + previousVersion
                );
            }

            log.info("Servidor " + server.getId() + ": deploy exitoso ✓");
        }

        // =========================================================
        // CORRECCIÓN #2 - Verificar consistencia entre TODOS los servidores
        // Esta verificación habría detectado el servidor #8 sin actualizar
        // en el incidente de Knight Capital.
        // =========================================================
        verifyVersionConsistency(newVersion, servers, previousVersion);

        log.info("Deploy completado y verificado en " + servers.size() + " servidores ✓");
        alertService.notify("Deploy exitoso: todos los servidores en versión " + newVersion);
    }

    /**
     * Verifica que TODOS los servidores ejecutan exactamente la misma versión.
     * Si hay divergencia, hace rollback automático.
     */
    private void verifyVersionConsistency(String expectedVersion,
                                           List<Server> servers,
                                           String fallbackVersion)
            throws DeploymentException {

        Set<String> runningVersions = servers.stream()
            .map(Server::getRunningVersion)
            .collect(Collectors.toSet());

        if (runningVersions.size() != 1) {
            // Hay servidores con versiones distintas → INCONSISTENTE
            log.severe("Inconsistencia detectada! Versiones activas: " + runningVersions);

            rollbackAll(fallbackVersion, servers);
            throw new DeploymentException(
                "Inconsistencia de versiones entre servidores: " + runningVersions +
                ". Rollback a " + fallbackVersion + " completado."
            );
        }

        String activeVersion = runningVersions.iterator().next();
        if (!activeVersion.equals(expectedVersion)) {
            log.severe("Versión activa (" + activeVersion + ") != esperada (" + expectedVersion + ")");
            rollbackAll(fallbackVersion, servers);
            throw new DeploymentException("Versión incorrecta activa. Rollback ejecutado.");
        }

        log.info("Consistencia verificada: todos los servidores en versión " + activeVersion);
    }

    // =========================================================
    // CORRECCIÓN #3 - Rollback real y automático
    // =========================================================
    private void rollbackAll(String version, List<Server> servers) {
        log.warning("Iniciando rollback a versión: " + version);

        for (Server server : servers) {
            boolean ok = server.deploy(version);
            if (ok) {
                log.info("Rollback exitoso en servidor: " + server.getId());
            } else {
                // Situación crítica: ni el rollback funcionó
                log.severe("ROLLBACK FALLIDO en servidor: " + server.getId());
                alertService.page("CRITICO: Rollback fallido en " + server.getId() +
                                  ". Intervención manual requerida.");
            }
        }
    }
}