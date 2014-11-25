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
(SELECT COUNT(DISTINCT(cfil.id))
          FROM {0}_vcs.files cfil
          JOIN {0}_vcs.actions ca ON ca.file_id = cfil.id
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
select * from {0}_issues.people;


-- inserts number of comments in issue and number of distinct commenters
ALTER TABLE {0}_issues.issues ADD COLUMN num_comments INT(11);
ALTER TABLE {0}_issues.issues ADD COLUMN num_commenters INT(11);

UPDATE {0}_issues.issues i SET i.num_comments =
(SELECT COUNT(DISTINCT(c.id))
          FROM {0}_issues.comments c
        WHERE c.issue_id = i.id),
i.num_commenters =
(SELECT COUNT(DISTINCT(c.submitted_by))
          FROM {0}_issues.comments c
        WHERE c.issue_id = i.id);