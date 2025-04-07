# Используем официальный образ OpenJDK 21 в качестве базового
FROM openjdk:21-jdk

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем файлы сборки в контейнер
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

# Собираем приложение без тестов
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

EXPOSE 8080

# Указываем команду запуска
ENTRYPOINT ["java", "-jar", "target/api_gateway-0.0.1-SNAPSHOT.jar"]
