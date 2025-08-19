# RTD Pipeline Development Environment Setup

This guide provides step-by-step installation instructions for setting up the development environment on macOS and Windows.

## Prerequisites Overview

- **Java 24 OpenJDK**
- **Git**
- **Maven 3.6+**
- **Docker Environment**

---

## macOS Installation Guide

### 1. Install Homebrew (if not already installed)
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### 2. Install Java 24 OpenJDK

#### Option A: Using Homebrew
```bash
# Add the OpenJDK tap
brew tap homebrew/cask-versions

# Install Java 24
brew install --cask temurin24

# Verify installation
java --version
```

#### Option B: Manual Installation
1. Download OpenJDK 24 from [Adoptium](https://adoptium.net/) or [OpenJDK](https://jdk.java.net/24/)
2. Run the installer package
3. Add to PATH in `~/.zshrc` or `~/.bash_profile`:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v24)
export PATH=$JAVA_HOME/bin:$PATH
```

### 3. Install Git
```bash
# Install via Homebrew
brew install git

# Configure Git
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# Verify installation
git --version
```

### 4. Install Maven 3.6+
```bash
# Install via Homebrew
brew install maven

# Verify installation (should show 3.6+ version)
mvn --version
```

### 5. Install Docker Environment with Colima

#### Install Colima (Recommended for macOS)
```bash
# Install Colima and Docker CLI
brew install colima docker docker-compose

# Start Colima with custom settings
colima start --cpu 4 --memory 8 --disk 100

# Verify Docker is working
docker run hello-world

# Optional: Create RTD-specific profile
colima start rtd-pipeline --cpu 4 --memory 8 --disk 50
```


### 6. Clone and Run the Project
```bash
# Clone the repository
git clone <repository-url>
cd rtd-gtfs-pipeline-refArch1

# Build the project
mvn clean compile

# Run the pipeline
./rtd-control.sh start
```

---

## Windows Installation Guide

### 1. Install Java 24 OpenJDK

#### Option A: Using Scoop
```powershell
# Install Scoop (if not installed)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex

# Install Java 24
scoop bucket add java
scoop install temurin24-jdk

# Verify installation
java --version
```

#### Option B: Manual Installation
1. Download OpenJDK 24 from [Adoptium](https://adoptium.net/) or [OpenJDK](https://jdk.java.net/24/)
2. Run the MSI installer
3. Add to System Environment Variables:
   - `JAVA_HOME`: `C:\Program Files\Eclipse Adoptium\jdk-24`
   - Add `%JAVA_HOME%\bin` to PATH

### 2. Install Git

#### Option A: Git for Windows
1. Download from [git-scm.com](https://git-scm.com/download/win)
2. Run installer with default settings
3. Open Git Bash and configure:
```bash
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

#### Option B: Using Scoop
```powershell
scoop install git
```

### 3. Install Maven 3.6+

#### Option A: Using Scoop
```powershell
scoop install maven

# Verify installation
mvn --version
```

#### Option B: Manual Installation
1. Download Maven from [maven.apache.org](https://maven.apache.org/download.cgi)
2. Extract to `C:\Program Files\Apache\maven`
3. Add to System Environment Variables:
   - `MAVEN_HOME`: `C:\Program Files\Apache\maven`
   - Add `%MAVEN_HOME%\bin` to PATH

### 4. Install Docker Environment

#### Option A: Podman (Recommended - No License Restrictions)
```powershell
# Using Scoop
scoop install podman

# Or download installer from https://podman.io/getting-started/installation
# Configure podman machine
podman machine init
podman machine start

# Create Docker compatibility alias
Set-Alias -Name docker -Value podman

# Verify
podman --version
```

**Note:** Podman is recommended for Windows users as it's free and open-source without licensing restrictions. It provides Docker-compatible commands and can run containers without requiring Docker Desktop's paid subscription for commercial use.

#### Option B: WSL2 with Docker (Alternative)
```powershell
# Enable WSL2
wsl --install

# Install Ubuntu from Microsoft Store
# Open Ubuntu and run:
sudo apt update
sudo apt upgrade

# Install Docker in WSL2
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Start Docker service
sudo service docker start

# Verify
docker --version
```

#### Option C: Docker Desktop (Note: Commercial License Required)
**Important:** Docker Desktop requires a paid subscription for commercial use in organizations with more than 250 employees or $10 million in revenue. Consider using Podman (Option A) to avoid licensing fees.

### 5. Clone and Run the Project

#### Using Git Bash or PowerShell:
```bash
# Clone the repository
git clone <repository-url>
cd rtd-gtfs-pipeline-refArch1

# Build the project
mvn clean compile

# Run the pipeline (Windows)
# Note: May need to adapt rtd-control.sh for Windows or use:
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDStaticDataPipeline"
```

#### Using WSL2:
```bash
# Open WSL2 Ubuntu terminal
cd /mnt/c/Users/YourUsername/Documents
git clone <repository-url>
cd rtd-gtfs-pipeline-refArch1

# Use Linux commands
./rtd-control.sh start
```

---

## Verification Commands

Run these commands to verify all installations:

### macOS / Linux / WSL2
```bash
# Check Java
java --version
javac --version

# Check Git
git --version

# Check Maven
mvn --version

# Check Docker
docker --version
docker-compose --version
docker run hello-world
```

### Windows PowerShell
```powershell
# Check Java
java --version
javac --version

# Check Git
git --version

# Check Maven
mvn --version

# Check Docker/Podman
docker --version
# or
podman --version
```

---

## Troubleshooting

### macOS Issues

#### Colima not starting
```bash
# Reset Colima
colima delete
colima start --cpu 4 --memory 8

# Check status
colima status
```

#### Java version conflicts
```bash
# List all Java versions
/usr/libexec/java_home -V

# Switch to Java 24
export JAVA_HOME=$(/usr/libexec/java_home -v24)
```

### Windows Issues

#### WSL2 Docker not starting
```powershell
# Restart WSL
wsl --shutdown
wsl

# In WSL, restart Docker
sudo service docker restart
```

#### Maven not recognized
- Ensure `MAVEN_HOME` and `JAVA_HOME` are set in System Environment Variables
- Restart terminal/PowerShell after setting environment variables

#### Path too long errors
- Enable long path support:
```powershell
# Run as Administrator
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force
```

---

## Quick Start After Installation

1. **Verify all tools are installed** using the verification commands above
2. **Clone the repository**
3. **Build the project**: `mvn clean compile`
4. **Run tests**: `mvn test`
5. **Start the pipeline**:
   - macOS/Linux: `./rtd-control.sh start`
   - Windows: `mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDStaticDataPipeline"`

---

## Additional Resources

- [OpenJDK Documentation](https://openjdk.org/projects/jdk/24/)
- [Maven Documentation](https://maven.apache.org/guides/)
- [Docker Documentation](https://docs.docker.com/)
- [Colima GitHub](https://github.com/abiosoft/colima)
- [WSL2 Documentation](https://docs.microsoft.com/en-us/windows/wsl/)
- [Podman Documentation](https://podman.io/docs)