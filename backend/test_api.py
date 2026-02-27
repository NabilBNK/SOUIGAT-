import requests

login_res = requests.post('http://localhost:8000/api/auth/login/', json={'phone':'0500000001', 'password':'admin123', 'platform':'web'})
token = login_res.json().get('access')
headers = {'Authorization': f'Bearer {token}'}

# test office creation
office_payload = {'name': 'Test Office API 2', 'city': 'Test City', 'is_active': True}
print('Office Create:', requests.post('http://localhost:8000/api/admin/offices/', json=office_payload, headers=headers).text)

