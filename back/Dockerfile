# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Imagem de produção não precisa dos testes: -Dmaven.test.skip pula COMPILAÇÃO e execução
# (o -DskipTests só pulava a execução, então um teste que não compila derrubava o deploy).
RUN mvn -q -B clean package -Dmaven.test.skip=true

# ---------- runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Railway injeta a variável PORT; a aplicação usa server.port=${PORT:8080}.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
