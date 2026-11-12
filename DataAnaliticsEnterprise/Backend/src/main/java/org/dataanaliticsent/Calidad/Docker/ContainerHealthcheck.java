package org.dataanaliticsent.Calidad.Docker;

/** Comprobación de salud sin instalar curl ni incluir credenciales en el proceso. */
public final class ContainerHealthcheck {
    public static void main(String[] args) {
        try {
            String url=System.getenv().getOrDefault("M2_HEALTHCHECK_URL","http://127.0.0.1:8080/api/v1/health");
            if (System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE","").contains("production") && !url.startsWith("https://")) System.exit(1);
            var connection=(java.net.HttpURLConnection)java.net.URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(2000);connection.setReadTimeout(3000);
            int status=connection.getResponseCode();connection.disconnect();
            if(status!=200)System.exit(1);
        }catch(Exception ex){System.exit(1);}
    }
}
