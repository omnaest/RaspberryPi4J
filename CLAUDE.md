# RaspberryPi4J

Multi-module aggregator POM (`org.omnaest.pi`) for Raspberry Pi hardware control. Contains no source itself — all code lives in submodules.

## Modules

| Module | Role |
|---|---|
| `pi-client-api` | Shared API interfaces between client and server |
| `pi-client` | Client library for consuming the Pi server remotely |
| `pi-server-api` | Server-side API interfaces (pi4j-free), incl. the `*SimulationControl` surfaces |
| `pi-server-platform-shared` | pi4j-free hardware-platform mechanics shared by the real + simulated impls and by `pi-server-core`: `AbstractAddressConnector` (`org.omnaest.pi.service.i2c`), the `PwmChipDriver` seam (`org.omnaest.pi.service.servo.chip`), `BitNumberUtils`/`ThreadUtils` (`org.omnaest.pi.service.utils`) |
| `pi-server-platform` | The **real, pi4j-backed** platform impls (`GPIOServiceImpl`, `I2CServiceImpl`, `PCA9685PwmChipDriver`); the **only** module with a `com.pi4j` dependency |
| `pi-server-platform-simulation` | The **simulated, in-memory** platform impls (`SimulatedGPIOServiceImpl`, `SimulatedI2CServiceImpl`, `SimulatedPwmChipDriver`) + their tests; **zero pi4j** on its classpath |
| `pi-server-core` | Higher-level sensor/actuator business services (depend only on the pi4j-free API + `pi-server-platform-shared`) |
| `pi-server` | Deployable Spring Boot server running on the Pi (depends on both platform modules so `@Profile` can select at runtime) |

### Hardware simulation (`simulation` Spring profile)

The hardware-platform layer runs off-Pi via a `simulation` Spring profile (introduced by plan-56; extracted into dedicated modules by plan-58). Each hardware-backed platform service has two beans gated by `@Profile("!simulation")` / `@Profile("simulation")`: the **real** one (pi4j-backed, `GPIOServiceImpl`, `I2CServiceImpl`, `PCA9685PwmChipDriver`) now lives in **`pi-server-platform`** (the only module with a `com.pi4j` dependency), and the **simulated**, in-memory one (`SimulatedGPIOServiceImpl`, `SimulatedI2CServiceImpl`, `SimulatedPwmChipDriver`) now lives in **`pi-server-platform-simulation`** (zero pi4j). Both keep their `org.omnaest.pi.service.{gpio,i2c,servo}.internal[.chip]` packages — `.internal` remains correct because nothing outside their own module imports them (Spring discovers them by component scan; callers use only the pi4j-free API interfaces). The simulated impls additionally implement a `*SimulationControl` interface (`pi-server-api`: `GPIOSimulationControl`, `I2CSimulationControl`, `ServoDriverSimulationControl`) for tests/interactive use to inject and inspect port/register/PWM state without hardware. `pi-server` depends on **both** platform modules unconditionally, so `@Profile` selects between them at runtime. When the profile is inactive, wiring and behavior are unchanged from before this pattern existed. `UltrasonicServiceImpl` (stays in `pi-server-core`) is a pure `GPIOService` consumer (no `@Profile` of its own) and works under either profile.

The pi4j-free mechanics shared across both platform modules and `pi-server-core` live in **`pi-server-platform-shared`**: `AbstractAddressConnector` (`org.omnaest.pi.service.i2c` — the shared I2C register/bit/wait template-method base, subclassed by the real `AddressConnectorImpl` and the `SimulatedAddressConnector`), the `PwmChipDriver` seam (`org.omnaest.pi.service.servo.chip`, channel-int addressed, technology-neutral name so the chip can be swapped), and `BitNumberUtils`/`ThreadUtils` (`org.omnaest.pi.service.utils`). These two contract types deliberately dropped their former `.internal` suffix when they became cross-module shared API. `ServoDriverServiceImpl` (stays in `pi-server-core`, package `org.omnaest.pi.service.servo.internal`) carries no `@Profile` and has zero pi4j imports — it consumes the `PwmChipDriver` seam via `@Autowired`. `PCA9685PwmChipDriver` (`@Profile("!simulation")`, in `pi-server-platform`) holds the pi4j I2C-bus discovery and `PCA9685GpioProvider` construction/shutdown; `SimulatedPwmChipDriver` (`@Profile("simulation")`, in `pi-server-platform-simulation`) holds in-memory 16-channel PWM state and also implements `ServoDriverSimulationControl`.

`pi-server/src/main/resources/application.yml` wires the profile: a default document (`spring.application.name: pi-server`) plus a `---`-separated document activated via `spring.config.activate.on-profile: simulation` that sets `logging.level.org.omnaest.pi: DEBUG`. Run interactively with the profile active by `cd`-ing into `pi-server` first — `mvn spring-boot:run "-Dspring-boot.run.profiles=simulation"` (or `SPRING_PROFILES_ACTIVE=simulation java -jar pi-server.jar` / `java -jar pi-server.jar --spring.profiles.active=simulation`). Note: `mvn -pl pi-server -am spring-boot:run ...` invoked from the RaspberryPi4J aggregator root does NOT work — the root is a plain `packaging=pom` aggregator with no Spring Boot parent, so Maven cannot resolve the `spring-boot` plugin prefix from there ("No plugin found for prefix 'spring-boot'"); the goal must be run with the working directory inside `pi-server` itself. Without the profile flag, the default (real, pi4j-backed) beans wire exactly as before this feature existed.

A cross-tier smoke test, `pi-server/src/test/java/org/omnaest/pi/SimulationProfileSmokeTest.java`, boots the full `Application` context under `@SpringBootTest(properties = "spring.profiles.active=simulation")` and drives `CompassService` end-to-end against a register preset via `I2CSimulationControl`, asserting the decoded angle — proving profile wiring and bean resolution, not just that the context loads.

As of this note, GPIO, I2C, servo/PCA9685 simulation, and `pi-server` profile wiring (plan-56) are all done, and the platform layer has been extracted from `pi-server-core` into the three dedicated `pi-server-platform*` modules (plan-58) so the module boundary itself confines pi4j to `pi-server-platform` and keeps `pi-server-core` + `pi-server-platform-simulation` provably pi4j-free.

## Build

```cmd
cd RaspberryPi4J
mvn clean install
```

Build order is resolved automatically by the Maven reactor (dependency graph): `pi-client-api`/`pi-server-api` → `pi-server-platform-shared` → `pi-server-platform`/`pi-server-platform-simulation`/`pi-server-core` → `pi-client`/`pi-server`.

## Publishing

Deploys to Sonatype OSSRH snapshots. No `release` profile in root POM — release is handled per submodule.
