package com.knightcapital.buggy;

import java.util.List;
import java.util.logging.Logger;

/**
 * Gestor de despliegue - Versión con errores
 *
 * ISSUES DETECTADOS POR SONARQUBE:
 *  [CRITICAL] Línea 34  - Resultado de deploy() ignorado (sin verificación)
 *  [CRITICAL] Línea 41  - Sin validación de consistencia entre servidores
 *  [MAJOR]    Línea 50  - Sin mecanismo de rollback ante fallo parcial
 *
 * CAUSA RAÍZ DEL DESASTRE:
 *  El 01/08/2012, este código desplegó la nueva versión en 7 de 8
 *  servidores. El servidor #8 quedó con PowerPeg activo.
 *  Nadie lo detectó porque no había verificación post-deploy.
 */
public class DeploymentManager {

    private static final Logger log = Logger.getLogger(DeploymentManager.class.getName());

    /*
     BUG #1 [CRITICAL] - SonarQube: java:S2201 / Return ignored
     deploy() retorna boolean indicando éxito, pero se ignora.
     Si un servidor falla silenciosamente, el deploy continúa.
    
    BUG #2 [CRITICAL] - Sin verificación post-deploy
    No se comprueba que TODOS los servidores ejecuten la misma
    versión después del despliegue.
    */
    public void deployToAllServers(String newVersion, List<Server> servers) {
        log.info("Iniciando deploy version: " + newVersion);

        for (Server server : servers) {
            server.deploy(newVersion);  // ← retorno boolean IGNORADO
            // SonarQube: "Return value of 'deploy' is ignored"
        }

        // ← No hay: verificación de versión activa,
        //           healthcheck post-deploy,
        //           confirmación de consistencia entre servidores,
        //           ni rollback si alguno falla.

        log.info("Deploy completado en " + servers.size() + " servidores");
        // ← Este mensaje aparece aunque 1 servidor haya fallado silenciosamente
    }

    // =========================================================
    // BUG #3 [MAJOR] - Sin rollback
    // Si el deploy falla a mitad, no hay forma de volver atrás.
    // Los servidores quedan en versiones inconsistentes.
    // =========================================================
    public void rollback(String previousVersion, List<Server> servers) {
        // Este método existe pero NUNCA es llamado por deployToAllServers()
        // en caso de error. Solo puede invocarse manualmente.
        for (Server server : servers) {
            server.deploy(previousVersion);  // ← mismo problema: return ignorado
        }
    }
}