# SVN to Git Migration Tool

A comprehensive bash script to migrate projects from SVN to GitHub while preserving commit history, branches, and tags.

## Features

- ✅ **Complete history preservation** - Maintains all commits, authors, and timestamps
- ✅ **Branch conversion** - Converts SVN branches to Git branches
- ✅ **Tag migration** - Converts SVN tags to proper Git tags
- ✅ **Author mapping** - Maps SVN usernames to proper Git identities
- ✅ **Automatic cleanup** - Removes SVN metadata and remotes
- ✅ **GitHub integration** - Pushes directly to GitHub with token support
- ✅ **Detailed reporting** - Generates migration statistics and summary

## Prerequisites

Install required tools:

```bash
# macOS
brew install git subversion git-svn

# Ubuntu/Debian
sudo apt-get install git subversion git-svn

# CentOS/RHEL
sudo yum install git subversion git-svn
```

## Quick Start

### Basic Migration
```bash
./scripts/svn-to-git.sh \
  --svn-url "https://svn.company.com/repo/project" \
  --git-repo "git@github.com:username/project.git" \
  --name "my-project"
```

### Full Migration with Authors Mapping
```bash
./scripts/svn-to-git.sh \
  --svn-url "https://svn.company.com/repo/project" \
  --git-repo "https://github.com/username/project.git" \
  --name "my-project" \
  --authors "authors.txt" \
  --token "$GITHUB_TOKEN"
```

## Usage Options

| Option | Short | Description | Example |
|--------|-------|-------------|---------|
| `--svn-url` | `-s` | SVN repository URL | `https://svn.company.com/repo` |
| `--git-repo` | `-g` | GitHub repository URL | `git@github.com:user/repo.git` |
| `--name` | `-n` | Local project directory name | `my-project` |
| `--authors` | `-a` | Authors mapping file | `authors.txt` |
| `--branches` | `-b` | SVN layout structure | `trunk,branches,tags` |
| `--token` | `-t` | GitHub personal access token | `ghp_xxxxxxxxxxxx` |
| `--temp-dir` |  | Custom temporary directory | `/tmp/migration` |
| `--no-history` |  | Skip history (faster migration) | |
| `--help` | `-h` | Show help message | |

## Authors File Format

Create an `authors.txt` file to map SVN usernames to Git identities:

```
# SVN to Git Authors Mapping
jdoe = John Doe <john.doe@company.com>
jsmith = Jane Smith <jane.smith@company.com>
admin = System Admin <admin@company.com>
```

The script can auto-generate this template by analyzing your SVN repository.

## Migration Process

The tool follows these steps:

1. **Dependency Check** - Verifies git, svn, and git-svn are installed
2. **Authors Analysis** - Extracts SVN authors and creates mapping template
3. **Repository Clone** - Uses `git-svn` to clone with full history
4. **Branch Conversion** - Converts SVN branches to local Git branches
5. **Tag Migration** - Converts SVN tags to proper Git tags
6. **Metadata Cleanup** - Removes SVN-specific metadata
7. **GitHub Push** - Pushes all branches and tags to GitHub
8. **Report Generation** - Creates detailed migration summary

## Examples

### RTD Internal Project Migration
```bash
# Migrate RTD project from internal SVN to GitHub
./scripts/svn-to-git.sh \
  -s "https://svn.rtd-denver.com/projects/transit-app" \
  -g "git@github.com:RTDDenver/transit-app.git" \
  -n "transit-app" \
  -a "rtd-authors.txt" \
  -t "$GITHUB_TOKEN"
```

### Large Repository with Custom Layout
```bash
# Handle non-standard SVN structure
./scripts/svn-to-git.sh \
  -s "https://svn.company.com/legacy/project" \
  -g "https://github.com/company/project.git" \
  -n "legacy-project" \
  -b "main,feature-branches,releases" \
  --temp-dir "/workspace/migration"
```

### Quick Migration Without History
```bash
# Fast migration for current state only
./scripts/svn-to-git.sh \
  -s "https://svn.company.com/small-project" \
  -g "git@github.com:user/small-project.git" \
  -n "small-project" \
  --no-history
```

## GitHub Token Setup

For HTTPS repositories, create a Personal Access Token:

1. Go to GitHub Settings → Developer settings → Personal access tokens
2. Generate new token with `repo` permissions
3. Use in command: `--token "ghp_your_token_here"`

For SSH repositories, ensure your SSH key is configured:
```bash
ssh-keygen -t ed25519 -C "your.email@company.com"
# Add to GitHub SSH keys
```

## Troubleshooting

### Common Issues

**Missing git-svn**
```bash
# macOS: Install via Homebrew
brew install git-svn

# Ubuntu: Install subversion package
sudo apt-get install git-svn
```

**SSL Certificate Issues**
```bash
# Accept SVN SSL certificates
svn info --non-interactive --trust-server-cert https://your-svn-url
```

**Large Repository Timeouts**
```bash
# Use custom temp directory on large disk
./scripts/svn-to-git.sh --temp-dir "/large/disk/migration" [other options]
```

**Authentication Issues**
```bash
# Test SVN access first
svn list https://your-svn-url

# Test GitHub access
git clone git@github.com:username/test-repo.git
```

### Migration Validation

After migration, verify:
```bash
cd migrated-repo

# Check commit count
git rev-list --all --count

# Verify branches
git branch -a

# Check tags
git tag

# Validate authors
git log --format='%an <%ae>' | sort -u
```

## Output Files

The migration creates:
- `migration-report.txt` - Detailed migration statistics
- `authors.txt` - Generated authors mapping (if not provided)
- Migrated Git repository ready for development

## Best Practices

1. **Test First** - Try migration on a small repository or branch
2. **Author Mapping** - Always use authors file for clean Git history
3. **Branch Strategy** - Plan your Git branching model before migration
4. **Repository Cleanup** - Archive SVN repository after successful migration
5. **Team Communication** - Notify team of new repository location and process

## Advanced Configuration

### Custom SVN Layout
```bash
# Non-standard SVN structure
--branches "development,main-branches,archive"
```

### Multiple Remotes
```bash
# Add additional remotes after migration
cd migrated-repo
git remote add backup git@gitlab.company.com:project.git
git push backup --all --tags
```

### Selective Migration
```bash
# Migrate specific SVN path
--svn-url "https://svn.company.com/repo/project/specific-module"
```

This tool provides a complete solution for migrating from SVN to GitHub while maintaining project history and team collaboration workflows.