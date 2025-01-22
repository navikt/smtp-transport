CREATE TABLE payload
(
    reference_id        VARCHAR(256)		NOT NULL,
    content_id		    VARCHAR(256)		NOT NULL,
    content_type		VARCHAR(256)		NOT NULL,
    content             BYTEA		        NOT NULL,
    created_at		    TIMESTAMP		    DEFAULT now(),
    PRIMARY KEY (reference_id, content_id)
);

INSERT INTO payload (reference_id, content_id, content_type, content)
VALUES (
	'99819a74-3f1d-453b-b1d3-735d900cfc5d',
	'f7aeef95-afca-4355-b6f7-1692e58c61cc',
	'text/xml',
	'<?xml version="1.0" encoding="utf-8"?><dummy>xml 1</dummy>'
	), (
    'df68056e-5cba-4351-9085-c37b925b8ddd',
    'tKV9FS_cSMy7IsQ41SHIUQ',
    'text/xml',
    '<?xml version="1.0" encoding="utf-8"?><dummy>xml 2</dummy>'
    ), (
    'df68056e-5cba-4351-9085-c37b925b8ddd',
    'test',
    'text/xml',
    '<?xml version="1.0" encoding="utf-8"?><dummy>xml 3</dummy>'
    )