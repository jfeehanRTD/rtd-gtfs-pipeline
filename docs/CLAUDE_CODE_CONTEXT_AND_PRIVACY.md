# Claude Code Context and Privacy Guide

## Overview

This document explains how Claude Code handles context, what data is shared with the LLM (Large Language Model), and what privacy considerations you should be aware of when using Claude Code in your development workflow.

## 📋 **What is Context in Claude Code?**

Context is the information that Claude Code has access to in order to provide relevant assistance. This includes:

- **File Contents**: Source code, configuration files, documentation
- **File Structure**: Directory layout, file names, project organization
- **Command History**: Previous commands run and their outputs
- **Conversation History**: Previous interactions in the current session
- **System Information**: Operating system, working directory, git status

## 🔍 **What Data is Shared with the LLM**

### **Always Shared:**
- **Your Messages**: Everything you type to Claude Code
- **File Contents**: When you read, edit, or search files
- **Command Outputs**: Results from bash commands, file operations
- **System Context**: Working directory, git branch, OS type
- **Project Structure**: File and directory names when explored

### **Shared When Tools Are Used:**
- **File Reads**: Complete file contents when using Read tool
- **Code Searches**: Search patterns and matching code snippets
- **Web Requests**: URLs and fetched content when using WebFetch/WebSearch
- **Git Information**: Branch names, commit messages, file changes
- **Network Information**: When running network diagnostic commands

### **Never Shared:**
- **Files Not Explicitly Accessed**: Files not read or searched by tools
- **Passwords**: Unless explicitly included in files or commands you run
- **SSH Keys**: Unless you explicitly read or display them
- **Environment Variables**: Unless explicitly accessed
- **Browser History/Bookmarks**: Not accessible to Claude Code
- **Other Applications**: Data from other running applications

## 🛡️ **Privacy and Security Considerations**

### **Sensitive Data Protection:**

#### **High Risk - Avoid These:**
```bash
# DON'T do these if you have sensitive data:
cat ~/.ssh/id_rsa                    # Exposes SSH private key
echo $DATABASE_PASSWORD              # Exposes passwords
cat config/secrets.yml               # Exposes API keys/secrets
git log --oneline -p                 # May expose sensitive commit content
```

#### **Safe Alternatives:**
```bash
# DO these instead:
ls ~/.ssh/                           # List SSH files without exposing keys
echo "Database config exists"        # Confirm without exposing values
ls config/                           # Check structure without reading secrets
git log --oneline                    # View commits without detailed content
```

### **Best Practices for Sensitive Projects:**

#### **1. Use .gitignore and File Exclusions**
```bash
# Your .gitignore should include:
*.env
*.env.local
config/secrets.yml
.env.production
ssh_keys/
credentials/
```

#### **2. Separate Sensitive Configuration**
```bash
# Good structure:
config/
├── app.yml              # Safe to share
├── database.example.yml # Template without real credentials
└── secrets/            # Excluded from git and Claude access
    ├── database.yml    # Real credentials
    └── api_keys.yml    # API keys
```

#### **3. Use Environment Variables Carefully**
```bash
# Instead of exposing in commands:
export DATABASE_URL="postgres://user:pass@host/db"

# Use placeholder references:
echo "DATABASE_URL is set: ${DATABASE_URL:+yes}"
```

## 🔧 **Controlling Context in Claude Code**

### **Limiting File Access:**
Claude Code only accesses files when:
- You explicitly use Read tool on a file
- You use Grep/Glob tools to search
- You edit files with Edit/Write tools
- You run commands that output file contents

### **Safe File Operations:**
```bash
# These are safe - no file contents shared:
ls -la                              # List files
find . -name "*.java" -type f      # Find files
wc -l src/**/*.java                # Count lines
git status                         # Check git status

# These expose file contents:
cat sensitive_file.txt             # Reads entire file
grep -r "password" .               # May find sensitive data
head config/database.yml           # Reads file content
```

### **Project Structure Visibility:**
Claude Code can see:
- ✅ File and directory names
- ✅ Git branch and status information
- ✅ Basic project structure
- ❌ File contents (unless explicitly read)
- ❌ File permissions or ownership details

## 📁 **RTD Pipeline Project Context**

### **What Claude Code Knows About This Project:**

#### **Safe Information Shared:**
- Project is an RTD GTFS-RT pipeline using Apache Flink and Kafka
- Java/Maven project structure
- Git branch names and commit history
- File names and directory organization
- Configuration file names (but not contents unless read)
- Script names and their purposes

#### **Potentially Sensitive (When Accessed):**
- Database connection strings (in config files)
- API keys for RTD services (if in code/config)
- Internal server names and IP addresses (like 10.4.51.37)
- User credentials for proxy services
- SSH server names and usernames

### **Current Project Data Exposure:**
Based on our work session:
- ✅ **RTD proxy IP**: 10.4.51.37 (shared during troubleshooting)
- ✅ **Local IP**: 192.168.0.45 (detected during network setup)
- ✅ **Username**: jamesfeehan (from system commands)
- ✅ **Project structure**: Complete understanding of codebase organization
- ✅ **Configuration details**: Kafka, Flink, Docker setup
- ❌ **Actual credentials**: Not exposed unless explicitly read from files

## 🚨 **When to Be Cautious**

### **Red Flags - Stop and Think:**
- Working with production configuration files
- Files containing API keys, passwords, or tokens
- SSH configuration or key files
- Database connection strings
- Internal security policies or documentation
- Personal or employee information

### **Safe Development Practices:**
```bash
# Create example/template files instead of reading real ones:
cp config/production.yml config/example.yml
# Edit example.yml to remove sensitive data, then share

# Use placeholder values:
database_url: "postgres://username:password@host:port/database"
api_key: "your-api-key-here"
```

## 📊 **Context Persistence**

### **What Persists Between Sessions:**
- ❌ **Nothing**: Each Claude Code session is independent
- ❌ **No chat history**: Previous conversations are not remembered
- ❌ **No file cache**: File contents are not stored between sessions

### **What's Shared in Current Session:**
- ✅ **Conversation history**: Everything in the current chat
- ✅ **File contents**: Any files read during this session
- ✅ **Command outputs**: All command results from this session
- ✅ **Context awareness**: Understanding built up during conversation

## 🔐 **Enterprise and Security Considerations**

### **For RTD/Corporate Use:**
- **Review company policies** on AI tool usage
- **Check data governance rules** about sharing code/configuration
- **Consider using on-premises** or private Claude deployments if available
- **Establish guidelines** for what can be shared with AI tools

### **Recommended Policies:**
```yaml
# Example AI Tool Usage Policy
allowed:
  - Public documentation
  - Non-sensitive source code
  - Development configurations (sanitized)
  - Test data and mock configurations

restricted:
  - Production configurations
  - API keys and credentials
  - Personal data
  - Proprietary algorithms
  - Security-related code

prohibited:
  - Customer data
  - Production secrets
  - SSH keys
  - Database dumps
  - Security vulnerabilities (unless for fixing)
```

## 🛠️ **Practical Tips for Safe Usage**

### **Before Starting a Session:**
1. **Review what you'll be working on**
2. **Identify sensitive files** to avoid
3. **Prepare sanitized examples** if needed
4. **Set up proper .gitignore** rules

### **During Development:**
1. **Use `ls` before `cat`** to check if files are safe
2. **Create templates** instead of sharing real configs
3. **Use placeholders** for sensitive values
4. **Review command outputs** before they're sent

### **Best Practices:**
```bash
# Good: Check structure without exposing content
ls config/
find . -name "*.yml" | head -10

# Good: Work with safe files
cat README.md
cat src/main/java/com/example/SafeClass.java

# Caution: These may expose sensitive data
cat .env
grep -r "password" .
cat config/database.yml
```

## 📞 **Questions and Support**

### **If You're Unsure:**
- **When in doubt, don't share**: It's better to be cautious
- **Ask your security team**: About AI tool policies
- **Use sanitized examples**: Create safe versions of sensitive files
- **Review before reading**: Think about what's in a file before using `cat` or `Read`

### **Getting Help Safely:**
- Describe problems without sharing actual credentials
- Use placeholder values in examples
- Create minimal reproducible examples with fake data
- Share error messages (which are usually safe) rather than configuration

## 🔍 **Summary**

Claude Code is a powerful development tool, but like any AI assistant, it requires thoughtful use when working with sensitive or proprietary code. By understanding what data is shared and following security best practices, you can leverage its capabilities while maintaining appropriate data protection.

**Remember**: Claude Code sees what you show it. Be intentional about what files you read, what commands you run, and what information you share during your development sessions.

---

*This document should be reviewed and updated based on your organization's specific security policies and requirements.*