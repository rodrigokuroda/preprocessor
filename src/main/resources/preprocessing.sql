-- inserts index for file path
ALTER TABLE {0}_vcs.file_links ADD INDEX (file_path) ;
ALTER TABLE {0}_vcs.scmlog ADD INDEX (rev(255)) ;
ALTER TABLE {0}_vcs.scmlog ADD INDEX (date) ;
-- Jira
ALTER TABLE {0}_issues.issues_ext_jira ADD INDEX (issue_key(32));
-- Bugzilla
ALTER TABLE {0}_issues.issues ADD INDEX (issue(255));
ALTER TABLE {0}_issues.changes ADD INDEX (changed_on) ;

-- inserts number of file in commit
ALTER TABLE {0}_vcs.scmlog ADD COLUMN num_files INT(11);

UPDATE {0}_vcs.scmlog s SET s.num_files =
(SELECT COUNT(DISTINCT(ca.file_id))
          FROM {0}_vcs.actions ca
         WHERE ca.commit_id = s.id);

-- Atribui 1 (zero) para autores que são desenvolvedores (committers)
-- quando possuem o mesmo nome ou usuário
-- Caso contrário (autor não é desenvolvedor), atribui 0 (zero)

ALTER TABLE {0}_issues.people CONVERT TO CHARACTER SET utf8 COLLATE utf8_general_ci;
ALTER TABLE {0}_issues.people ADD COLUMN is_dev tinyint(4) DEFAULT '0';

UPDATE {0}_issues.people ip SET ip.is_dev = 1
WHERE EXISTS
(SELECT 1
   FROM {0}_vcs.people sp
  WHERE upper(sp.name) = upper(ip.name) OR upper(sp.name) = upper(ip.user_id));
UPDATE {0}_issues.people ip SET ip.is_dev = 0
WHERE NOT EXISTS
(SELECT 1
   FROM {0}_vcs.people sp
  WHERE upper(sp.name) = upper(ip.name) OR upper(sp.name) = upper(ip.user_id));

-- inserts number of comments in issue and number of distinct commenters
ALTER TABLE {0}_issues.issues ADD COLUMN num_comments INT(11);
ALTER TABLE {0}_issues.issues ADD COLUMN num_commenters INT(11);
ALTER TABLE {0}_issues.issues ADD COLUMN num_dev_commenters INT(11);
ALTER TABLE {0}_issues.issues ADD COLUMN num_watchers INT(11);
ALTER TABLE {0}_issues.issues ADD COLUMN reopened_times INT(11);
ALTER TABLE {0}_issues.issues ADD COLUMN fixed_on DATETIME;

UPDATE {0}_issues.issues i SET i.fixed_on =
  (SELECT MAX(c.changed_on)
     FROM {0}_issues.changes c
    WHERE c.issue_id = i.id
      AND c.field = "Resolution"
      AND c.new_value = "Fixed")
WHERE i.resolution = "Fixed";

UPDATE {0}_issues.issues i SET
i.num_comments =
(SELECT COUNT(DISTINCT(c.id))
   FROM {0}_issues.comments c
  WHERE c.issue_id = i.id),

i.num_commenters =
(SELECT COUNT(DISTINCT(c.submitted_by))
   FROM {0}_issues.comments c
  WHERE c.issue_id = i.id),

i.num_dev_commenters =
(SELECT COUNT(DISTINCT(c.submitted_by))
   FROM {0}_issues.comments c
   JOIN {0}_issues.people p ON c.submitted_by = p.id
  WHERE c.issue_id = i.id
    AND p.is_dev = 1),

i.num_watchers =
(SELECT COUNT(DISTINCT(iw.person_id))
   FROM {0}_issues.issues_watchers iw
  WHERE iw.issue_id = i.id),

i.reopened_times =
(SELECT COALESCE(COUNT(1), 0)
   FROM {0}_issues.changes c
  WHERE c.new_value = "Reopened"
    AND c.field = "Status"
    AND c.issue_id = i.id);

-- denormalize vcs schema
CREATE SCHEMA {0};

CREATE TABLE IF NOT EXISTS {0}.commits (
    commit_id INT(11), -- scmlog
    rev VARCHAR(40), -- scmlog
    committer_id INT(11), -- scmlog
    date DATETIME, -- scmlog
    message LONGTEXT, -- scmlog
    repository_id INT(11), -- scmlog, files
    action_type VARCHAR(1), -- actions
    branch_id INT(11),  -- actions
    file_id INT(11), -- actions, files
    file_path VARCHAR(4096), -- file_links
    added_lines INT(11), -- commits_files_lines
    removed_lines INT(11), -- commits_files_lines
    KEY commit_id (commit_id),
    KEY committer_id (committer_id),
    KEY date (date),
    KEY repository_id (repository_id),
    KEY action_type (action_type),
    KEY branch_id (branch_id),
    KEY file_id (file_id),
    KEY file_path (file_path)
);


INSERT INTO {0}.commits (commit_id, rev, committer_id, date, message, repository_id, action_type, branch_id, file_id, file_path, added_lines, removed_lines)
SELECT DISTINCT s.id, s.rev, s.committer_id, s.date, s.message, s.repository_id, a.type, a.branch_id, fil.id, fill.file_path, filcl.added, filcl.removed
  FROM {0}_vcs.scmlog s
  JOIN {0}_vcs.actions a ON a.commit_id = s.id
  JOIN {0}_vcs.files fil ON fil.id = a.file_id
  JOIN {0}_vcs.file_links fill ON fill.file_id = fil.id AND fill.commit_id IN
	   (SELECT afill.commit_id
		  FROM {0}_vcs.file_links afill
		 WHERE afill.commit_id <= s.id
		   AND afill.file_id = fil.id
           AND afill.file_path LIKE CONCAT("%", fil.file_name))
  JOIN {0}_vcs.commits_files_lines filcl ON filcl.commit = s.id AND filcl.path = fill.file_path
 WHERE s.id IN (SELECT DISTINCT(scmlog_id) FROM {0}_issues.issues_scmlog)
 ORDER BY date ASC;