# Kommons
Kommons is a simple Spigot/Paper library to simplify plugin development.

![GitHub Release](https://img.shields.io/github/v/release/kaepsis/kommons)

---

## Installation
Replace `VERSION` with the latest version release you find in the **release** badge

### Gradle
```groovy
repositories {
    maven { url = "https://jitpack.io" }
}
dependencies {
    implementation 'com.github.kaepsis:kommons:VERSION'
}
```
### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
<dependencies>
    <dependency>
        <groupId>com.github.kaepsis</groupId>
        <artifactId>kommons</artifactId>
        <version>VERSION</version>
    </dependency>
</dependencies>
```

---

# Features

- Chat Color
- Config (Annotations) + i18n
- Database Management
    - DAO (AbstractDao\<K>)
    - Annotations
        - Table
        - Column
    - QueryManager
- Cooldown Management
    - SingleCooldownManager
    - ByActionCooldownManager
- Location Utils
- Time Utils
- Data Structures
    - ExpiringSet