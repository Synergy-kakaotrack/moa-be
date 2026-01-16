-- 1. 개발용 기본 사용자
INSERT INTO users (
    user_id,
    user_email,
    user_name,
    profile_url,
    created_at
) VALUES (
             1,
             'lion01@gmail.com',
             'Lion',
             'https://example.com/profiles/lion.png',
             NOW()
         )
    ON CONFLICT (user_id) DO NOTHING;


-- 2. 개발용 기본 프로젝트 1개
INSERT INTO projects (
    project_id,
    user_id,
    project_name,
    project_description,
    project_created_at,
    project_updated_at
) VALUES (
             1,
             1,  -- user_id = 1 (Lion)
             'MOA 개발 프로젝트1',
             'MOA 서비스 개발을 위한 프로젝트1',
             NOW(),
             NOW()
         ),
         (
             2,
             1,
             'MOA 개발 프로젝트2',
             'MOA 서비스 개발을 위한 프로젝트2',
             NOW(),
             NOW()
         ),
         (
             3,
             1,
             'MOA 개발 프로젝트3',
             'MOA 서비스 개발을 위한 프로젝트3',
             NOW(),
             NOW()
         )
    ON CONFLICT (project_id) DO NOTHING;
