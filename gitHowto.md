# Git Workflow and Best Practices Guide

This guide outlines the recommended Git workflow for feature development, bug fixes, and the pull request process for the RTD GTFS Pipeline project.

## Branch Structure

### Main Branches
- **`main`**: Production-ready code. Always stable and deployable.
- **`dev`**: Integration branch for features. Contains the latest development changes.

### Supporting Branches
- **Feature branches**: `feature/feature-name`
- **Bug fix branches**: `bugfix/issue-description` or `hotfix/critical-fix`
- **Release branches**: `release/v1.2.0` (when needed)

## Feature Development Workflow

### 1. Starting a New Feature

```bash
# Ensure you're on the latest dev branch
git checkout dev
git pull origin dev

# Create and switch to a new feature branch
git checkout -b feature/dynamic-interval-control

# Alternative: create branch from specific commit
git checkout -b feature/my-feature dev
```

### 2. Working on the Feature

```bash
# Make your changes and commit regularly
git add .
git commit -m "Add initial interval control component"

# Continue development with atomic commits
git add src/components/UpdateIntervalControl.tsx
git commit -m "Implement preset buttons for interval control"

git add src/services/rtdDataService.ts
git commit -m "Add setUpdateInterval method to data service"
```

### 3. Keeping Feature Branch Updated

```bash
# Regularly sync with dev to avoid conflicts
git checkout dev
git pull origin dev
git checkout feature/dynamic-interval-control
git merge dev

# Alternative: use rebase for cleaner history
git rebase dev
```

### 4. Completing the Feature

```bash
# Push feature branch to remote
git push origin feature/dynamic-interval-control -u

# Create pull request (using GitHub CLI)
gh pr create --title "Add dynamic update interval control" \
  --body "Feature description and changes" \
  --base dev
```

## Bug Fix Workflow

### 1. Bug Fixes for Development

```bash
# Create bug fix branch from dev
git checkout dev
git pull origin dev
git checkout -b bugfix/fix-process-detection

# Make fixes and test
git add rtd-control.sh
git commit -m "Fix React process detection in control script"

# Push and create PR to dev
git push origin bugfix/fix-process-detection -u
gh pr create --title "Fix process detection in rtd-control.sh" --base dev
```

### 2. Hotfixes for Production

```bash
# Create hotfix branch from main for critical issues
git checkout main
git pull origin main
git checkout -b hotfix/critical-security-fix

# Make minimal fix
git add src/main/java/SecurityFix.java
git commit -m "Fix critical security vulnerability in API endpoint"

# Push and create PR to main (expedited review)
git push origin hotfix/critical-security-fix -u
gh pr create --title "HOTFIX: Critical security vulnerability" --base main

# After merge, also merge to dev
git checkout dev
git merge main
```

## Pull Request Process

### 1. Creating a Pull Request

```bash
# Using GitHub CLI (recommended)
gh pr create --title "Clear, descriptive title" \
  --body "$(cat <<'EOF'
## Summary
Brief description of changes

## Changes Made
- Added feature X
- Fixed issue Y  
- Updated documentation

## Test Plan
- [x] Manual testing completed
- [x] Unit tests pass
- [ ] Integration tests needed

## Breaking Changes
None / List any breaking changes

🤖 Generated with [Claude Code](https://claude.ai/code)
EOF
)" \
  --base dev

# Using web interface
# Navigate to GitHub and click "New Pull Request"
```

### 2. PR Template Structure

```markdown
## Summary
Brief description of what this PR accomplishes

## Type of Change
- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that causes existing functionality to change)
- [ ] Documentation update

## Changes Made
- Bullet point list of specific changes
- Include file names and key modifications
- Mention any new dependencies or configuration changes

## Test Plan
- [ ] Manual testing completed
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Performance impact assessed

## Screenshots/Demos
Include relevant screenshots or links to demo videos

## Breaking Changes
Describe any breaking changes and migration steps

## Additional Notes
Any additional context, considerations, or follow-up work needed
```

### 3. PR Review Process

#### For Authors:
1. **Self-review** before requesting review
2. **Ensure CI/CD passes** all checks
3. **Address feedback promptly** and push updates
4. **Resolve conflicts** if base branch updates

#### For Reviewers:
1. **Check code quality** and style consistency
2. **Verify functionality** matches requirements
3. **Test locally** if needed
4. **Provide constructive feedback**
5. **Approve when satisfied**

### 4. Merging Strategies

#### Merge Commit (Default)
```bash
# Creates merge commit preserving branch history
git checkout dev
git merge --no-ff feature/dynamic-interval-control
```

#### Squash and Merge (Recommended for features)
```bash
# Combines all commits into single commit
# Done through GitHub interface or:
git checkout dev
git merge --squash feature/dynamic-interval-control
git commit -m "Add dynamic update interval control

- Created UpdateIntervalControl component
- Added Java API endpoint for configuration
- Fixed process management in control script"
```

#### Rebase and Merge (For clean history)
```bash
# Replays commits without merge commit
git checkout feature/dynamic-interval-control
git rebase dev
git checkout dev
git merge feature/dynamic-interval-control
```

## Commit Message Best Practices

### Format
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
- **feat**: New feature
- **fix**: Bug fix
- **docs**: Documentation changes
- **style**: Code style changes (formatting, etc.)
- **refactor**: Code refactoring
- **test**: Adding or updating tests
- **chore**: Maintenance tasks

### Examples
```bash
# Good commit messages
git commit -m "feat(ui): add dynamic interval control component

- Implement preset buttons for common intervals
- Add custom input for arbitrary values
- Include performance warnings for high frequency"

git commit -m "fix(control): resolve React process detection issues

The process matching was too strict and failed to detect
running React development servers in some environments."

git commit -m "docs: add Git workflow guide for team development"
```

## Branch Protection and CI/CD

### Protected Branch Rules

```yaml
# GitHub branch protection settings
dev:
  required_status_checks: true
  enforce_admins: true
  required_pull_request_reviews:
    required_approving_review_count: 1
    dismiss_stale_reviews: true
  restrictions: null

main:
  required_status_checks: true
  enforce_admins: true
  required_pull_request_reviews:
    required_approving_review_count: 2
    dismiss_stale_reviews: true
    require_code_owner_reviews: true
```

### Continuous Integration

```bash
# Typical CI checks that must pass
- Maven build and tests
- ESLint and TypeScript compilation
- Jest unit tests
- Integration test suite
- Security scans
- Code coverage thresholds
```

## Common Scenarios and Commands

### Updating Local Branches
```bash
# Update all local branches with remote changes
git fetch --all
git checkout dev
git pull origin dev
git checkout main
git pull origin main
```

### Cleaning Up Old Branches
```bash
# List merged branches
git branch --merged dev

# Delete local merged branches (except main/dev)
git branch --merged dev | grep -v "main\|dev" | xargs -n 1 git branch -d

# Delete remote tracking branches that no longer exist
git remote prune origin
```

### Resolving Merge Conflicts
```bash
# When conflicts occur during merge/rebase
git status                    # See conflicted files
# Edit files to resolve conflicts
git add <resolved-files>
git commit                    # For merge
git rebase --continue        # For rebase
```

### Emergency Procedures

#### Reverting a Bad Commit
```bash
# Revert a specific commit (creates new commit)
git revert <commit-hash>

# Revert merge commit
git revert -m 1 <merge-commit-hash>
```

#### Rolling Back a Release
```bash
# Create hotfix branch from previous good commit
git checkout main
git checkout -b hotfix/rollback-v1.2.0 <good-commit-hash>
git push origin hotfix/rollback-v1.2.0 -u
# Create emergency PR to main
```

## Project-Specific Workflows

### RTD Pipeline Development
```bash
# 1. Feature development
git checkout dev
git pull origin dev
git checkout -b feature/kafka-integration

# 2. Development with testing
mvn clean compile              # Java compilation
npm test                      # React tests
./rtd-control.sh status       # System integration

# 3. Commit and push
git add .
git commit -m "feat(pipeline): add Kafka message publishing"
git push origin feature/kafka-integration -u

# 4. Create PR
gh pr create --title "Add Kafka integration for real-time messaging" --base dev
```

### Release Process
```bash
# 1. Create release branch
git checkout dev
git pull origin dev
git checkout -b release/v1.3.0

# 2. Version bumps and final testing
# Update version numbers in pom.xml, package.json
git commit -m "chore: bump version to 1.3.0"

# 3. Create PR to main
gh pr create --title "Release v1.3.0" --base main

# 4. After merge, tag the release
git checkout main
git pull origin main
git tag -a v1.3.0 -m "Release version 1.3.0"
git push origin v1.3.0

# 5. Merge back to dev
git checkout dev
git merge main
git push origin dev
```

## Best Practices Summary

1. **Always branch from the target branch** (dev for features, main for hotfixes)
2. **Keep branches small and focused** on a single feature or fix
3. **Use descriptive branch and commit messages**
4. **Test thoroughly** before creating PRs
5. **Keep PRs up-to-date** with target branch
6. **Review your own PR** before requesting others to review
7. **Clean up branches** after merging
8. **Use protected branches** for main integration points
9. **Automate testing** with CI/CD pipelines
10. **Document decisions** and breaking changes clearly

This workflow ensures code quality, maintains a clean history, and enables safe collaboration across the development team.