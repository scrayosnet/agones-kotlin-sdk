# Contributing

We're always open to contributions to this project! Below are some small guidelines to follow when submitting any Pull
Requests for this project. Thanks for contributing!

## Code of Conduct

Participation in this project comes under the [Contributor Covenant Code of Conduct][code-of-conduct].

## Pull Requests

In order to contribute to this project, please note:

* We follow the [GitHub Pull Request Model][github-pull-request-model] for all contributions.
* For new features, documentation **must** be included.
* All submissions require review before being merged.
* Once review has occurred, please squash your Pull Request into a single commit and rebase it.
* Write [meaningful commit message][commit-messages] for your contribution.
* See this [blog post][logging-levels] for choosing the right logging levels.
* Please follow the code formatting instructions below.

### Formatting

Please note the following rules for formatting your code:

* Format all Kotlin code with [ktlint][ktlint-docs]. You can invoke a check by running `./gradlew ktlintCheck` and fix
  most errors automatically with `./gradlew ktlintFormat`. The relevant configuration for ktlint resides within our
  project in our [.editorconfig][editor-config-file].
* Remove trailing whitespaces in all files.
* Ensure any new or modified files have a [trailing newline][trailing-newline-stackoverflow].

This project comes also with an [.editorconfig][editorconfig-docs] that should already handle most of the cases outlined
above will always be extended to match these criteria as close as possible.

### Continuous Integration

Automatic checks are performed through [GitHub Actions][github-actions-docs] and run for every submitted Pull Request.
They are all expected to run without any warnings or errors, until the Pull Request will be merged. Please look into the
output of the Workflows that were executed on your Pull Request to check whether everything complies with our checks.

[code-of-conduct]: CODE_OF_CONDUCT.md

[github-pull-request-model]: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests

[commit-messages]: https://cbea.ms/git-commit/

[logging-levels]: https://medium.com/@tom.hombergs/tip-use-logging-levels-consistently-913b7b8e9782

[ktlint-docs]: https://pinterest.github.io/ktlint/latest/

[editor-config-file]: .editorconfig

[trailing-newline-stackoverflow]: https://stackoverflow.com/questions/5813311/no-newline-at-end-of-file

[editorconfig-docs]: https://editorconfig.org/

[github-actions-docs]: https://github.com/features/actions
