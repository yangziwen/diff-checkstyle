#!/bin/bash

opts="$*"

PRE_COMMIT_BLOB_ID="300274dbb55070da156ac594c97b77cbd4c1f5c5"
PRE_COMMIT_FILE_URL="https://raw.githubusercontent.com/yangziwen/diff-checkstyle/master/hooks/pre-commit"
DIFF_CHECKSTYLE_GITHUB_URL="https://github.com/yangziwen/diff-checkstyle"

function get_value_from_opts() {
    key="--$1="
    value=`echo "${opts##*${key}}" | awk -F' --' '{print $1}'`
    if [[ ! "$value" =~ ^-- ]]; then
        echo "$value"
        return 0
    fi
    if [[ "$opts" =~ --$1($|\ ) ]]; then
        echo "true"
        return 0
    fi
    echo ""
}

function is_diff_checkstyle_pre_commit_script() {
    if [[ "$PRE_COMMIT_BLOB_ID" == `git hash-object $1` ]]; then
        return 0
    fi
    return 1
}

function get_pre_commit_script_path() {
    hook_names=("pre-commit" "hooks/pre-commit" "pre-commit-diff-checkstyle")
    for hook_name in ${hook_names[@]}; do
        [ -f $hook_name ] && is_diff_checkstyle_pre_commit_script $hook_name && echo "$hook_name" && exit 0
    done
    curl $PRE_COMMIT_FILE_URL > pre-commit-diff-checkstyle
    echo "pre-commit-diff-checkstyle"
}

function get_diff_checkstyle_jar_path() {
    if [[ -f diff-checkstyle.jar ]]; then
        echo "diff-checkstyle.jar" && exit 0
    fi
    if [[ -f target/diff-checkstyle.jar ]]; then
        echo "target/diff-checkstyle.jar" && exit 0
    fi
    if [[ -f diff-checkstyle/target/diff-checkstyle.jar ]]; then
        echo "diff-checkstyle/target/diff-checkstyle.jar" && exit 0
    fi
    git clone --depth 1 $DIFF_CHECKSTYLE_GITHUB_URL
    cd diff-checkstyle && mvn package 2>&1 > /dev/null
    echo "diff-checkstyle/target/diff-checkstyle.jar"
}

function install_global_hook() {
    hook_path="`git config --global --get core.hooksPath`"
    if [[ -z "$hook_path" ]]; then
        git config --global core.hooksPath ~/.githooks
        hook_path="`git config --global --get core.hooksPath`"
    fi
    if [[ ! -d $hook_path ]]; then
        mkdir -p $hook_path
    fi
    pre_commit_script_path="`get_pre_commit_script_path`"
    diff_checkstyle_jar_path="`get_diff_checkstyle_jar_path`"
    cp $pre_commit_script_path $hook_path/pre-commit
    cp $diff_checkstyle_jar_path $hook_path/diff-checkstyle.jar
    echo "diff-checkstyle pre-commit hook is intalled globally!"
}

function install_hook_to_repo() {
    repo_path="$1"
    if [ ! -d $repo_path ]; then
        echo "repo[$repo_path] does not exist!"
        exit 1
    fi
    hook_path="$repo_path/.git/hooks"
    pre_commit_script_path="`get_pre_commit_script_path`"
    diff_checkstyle_jar_path="`get_diff_checkstyle_jar_path`"
    cp $pre_commit_script_path $hook_path/pre-commit
    cp $diff_checkstyle_jar_path $hook_path/diff-checkstyle.jar
    chmod +x $hook_path/pre-commit
    echo "diff-checkstyle pre-commit hook is installed to $repo_path!"
}

global="`get_value_from_opts global`"

repo_path="`get_value_from_opts repo-path`"

if [[ "true" == "$global" ]]; then
    install_global_hook
    exit 0
fi

if [[ -n "$repo_path" ]]; then
    install_hook_to_repo $repo_path
    exit 0
fi

echo "Please specify the options correctly"
echo "  --global => install the diff-checkstyle hook globally"
echo "  --repo-path=\${the_absolute_path_of_your_git_repository} => install the diff-checkstyle hook to the specified git repository"
